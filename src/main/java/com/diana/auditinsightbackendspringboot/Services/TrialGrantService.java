package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Admin.AdminAuditService;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialEventType;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialSource;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.TrialCampaign;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Models.TrialGrantEvent;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantEventRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super Admin-authorised free trials. A trial exists only as a {@link TrialGrant}: granted individually by a Super
 * Admin, or through an enabled campaign when an eligible organisation registers. Durations and dates are always
 * computed here, never taken from the organisation.
 * <p>
 * Paid time bought during a trial starts when the trial ends. So whenever a trial's end moves (extended, shortened
 * or revoked), paid periods that haven't started yet move by the same amount and keep their length.
 * <p>
 * Every change runs under {@link OrganisationLock} (one transaction, serialised with payment activation), is
 * recorded in {@code trial_grant_events}, and — for Super Admin actions — in the admin audit log with a reason.
 */
@Service
@Slf4j
public class TrialGrantService {

    public static final int MAX_TRIAL_DAYS = 365;

    /** Grants and their full history, for the admin screen. */
    public record TrialHistory(List<TrialGrant> grants, List<TrialGrantEvent> events) {}

    private final TrialGrantRepository grantRepo;
    private final TrialGrantEventRepository eventRepo;
    private final OrganisationRepository organisationRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final UserRepository userRepo;
    private final AdminAuditService adminAuditService;
    private final OrganisationLock organisationLock;

    public TrialGrantService(TrialGrantRepository grantRepo,
                             TrialGrantEventRepository eventRepo,
                             OrganisationRepository organisationRepo,
                             SubscriptionRepository subscriptionRepo,
                             UserRepository userRepo,
                             AdminAuditService adminAuditService,
                             OrganisationLock organisationLock) {
        this.grantRepo = grantRepo;
        this.eventRepo = eventRepo;
        this.organisationRepo = organisationRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.userRepo = userRepo;
        this.adminAuditService = adminAuditService;
        this.organisationLock = organisationLock;
    }

    // ── Super Admin actions ───────────────────────────────────────────────────────

    /** Grants a trial of {@code days} starting now. Fails if the organisation already has a current trial. */
    public Mono<TrialGrant> grant(UUID organisationId, int days, String reason, String adminEmail) {
        if (days < 1 || days > MAX_TRIAL_DAYS) {
            return Mono.error(new InvalidRecord("A trial must be between 1 and " + MAX_TRIAL_DAYS + " days"));
        }
        return admin(adminEmail).flatMap(admin -> organisation(organisationId).flatMap(org ->
                organisationLock.inTransaction(organisationId, () -> closeExpiredOpenGrant(organisationId, admin.getId())
                        .then(grantRepo.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE)
                                .flatMap(existing -> Mono.<TrialGrant>error(new InvalidRecord(
                                        "This organisation already has an active trial until " + existing.getEndsAt()
                                                + ". Extend or shorten it instead.")))
                                .switchIfEmpty(Mono.defer(() -> createGrant(org, TrialSource.INDIVIDUAL, null, days,
                                        admin.getId(), reason))))
                        .flatMap(saved -> adminAuditService.record(admin.getId(), "GRANT_TRIAL", "ORGANISATION",
                                        organisationId.toString(), null, summary(saved), reason)
                                .thenReturn(saved)))));
    }

    /**
     * Moves the end of the organisation's current trial to {@code newEndsAt} (extend or shorten). The new end must
     * be in the future; use {@link #revoke} to end a trial now. Unstarted paid periods move by the same amount.
     */
    public Mono<TrialGrant> changeEnd(UUID organisationId, LocalDateTime newEndsAt, String reason, String adminEmail) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new InvalidRecord("A reason is required to change a trial"));
        }
        LocalDateTime now = LocalDateTime.now();
        if (newEndsAt == null || !newEndsAt.isAfter(now)) {
            return Mono.error(new InvalidRecord("The new end date must be in the future. To end the trial now, revoke it."));
        }
        return admin(adminEmail).flatMap(admin -> organisationLock.inTransaction(organisationId, () ->
                currentGrant(organisationId).flatMap(grant -> {
                    if (!newEndsAt.isAfter(grant.getStartsAt())) {
                        return Mono.error(new InvalidRecord("The new end date must be after the trial started"));
                    }
                    if (grant.getStartsAt().plusDays(MAX_TRIAL_DAYS).isBefore(newEndsAt)) {
                        return Mono.error(new InvalidRecord("A trial cannot last more than " + MAX_TRIAL_DAYS + " days"));
                    }
                    Map<String, Object> before = summary(grant);
                    LocalDateTime oldEndsAt = grant.getEndsAt();
                    TrialEventType type = newEndsAt.isAfter(oldEndsAt) ? TrialEventType.EXTENDED : TrialEventType.SHORTENED;
                    grant.setEndsAt(newEndsAt);
                    grant.setReminderSent(false);
                    grant.setUpdatedAt(now);
                    return grantRepo.save(grant)
                            .flatMap(saved -> event(saved, type, oldEndsAt, newEndsAt, admin.getId(), reason, null)
                                    .then(moveUnstartedPaidPeriods(saved, oldEndsAt, Duration.between(oldEndsAt, newEndsAt),
                                            admin.getId(), reason))
                                    .then(adminAuditService.record(admin.getId(),
                                            type == TrialEventType.EXTENDED ? "EXTEND_TRIAL" : "SHORTEN_TRIAL",
                                            "ORGANISATION", organisationId.toString(), before, summary(saved), reason))
                                    .thenReturn(saved));
                })));
    }

    /** Ends the organisation's current trial now. Unstarted paid periods move earlier so they start now. */
    public Mono<TrialGrant> revoke(UUID organisationId, String reason, String adminEmail) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new InvalidRecord("A reason is required to revoke a trial"));
        }
        LocalDateTime now = LocalDateTime.now();
        return admin(adminEmail).flatMap(admin -> organisationLock.inTransaction(organisationId, () ->
                currentGrant(organisationId).flatMap(grant -> {
                    Map<String, Object> before = summary(grant);
                    LocalDateTime oldEndsAt = grant.getEndsAt();
                    grant.setStatus(TrialGrantStatus.REVOKED);
                    grant.setRevokedAt(now);
                    grant.setRevokedBy(admin.getId());
                    grant.setRevokeReason(reason.trim());
                    grant.setUpdatedAt(now);
                    return grantRepo.save(grant)
                            .flatMap(saved -> event(saved, TrialEventType.REVOKED, oldEndsAt, now, admin.getId(), reason, null)
                                    .then(moveUnstartedPaidPeriods(saved, oldEndsAt, Duration.between(oldEndsAt, now),
                                            admin.getId(), reason))
                                    .then(adminAuditService.record(admin.getId(), "REVOKE_TRIAL", "ORGANISATION",
                                            organisationId.toString(), before, summary(saved), reason))
                                    .thenReturn(saved));
                })));
    }

    public Mono<TrialHistory> history(UUID organisationId) {
        return organisation(organisationId).then(Mono.zip(
                grantRepo.findAllByOrganisationIdOrderByCreatedAtDesc(organisationId).collectList(),
                eventRepo.findAllByOrganisationIdOrderByCreatedAtAsc(organisationId).collectList()))
                .map(t -> new TrialHistory(t.getT1(), t.getT2()));
    }

    // ── Campaign grants (at registration) ─────────────────────────────────────────

    /**
     * Grants a campaign trial to a newly registered organisation. Returns empty (no trial) when the owner already
     * had a campaign trial — the database also enforces this, so concurrent registrations can't both receive one.
     */
    public Mono<TrialGrant> grantFromCampaign(Organisation org, TrialCampaign campaign) {
        return grantRepo.existsByOwnerClientIdAndSource(org.getClientId(), TrialSource.CAMPAIGN)
                .flatMap(alreadyHad -> alreadyHad
                        ? Mono.<TrialGrant>empty()
                        : organisationLock.inTransaction(org.getId(), () ->
                                grantRepo.findFirstByOrganisationIdAndStatus(org.getId(), TrialGrantStatus.ACTIVE)
                                        .flatMap(existing -> Mono.<TrialGrant>empty())
                                        .switchIfEmpty(Mono.defer(() -> createGrant(org, TrialSource.CAMPAIGN,
                                                campaign.getId(), campaign.getDurationDays(), campaign.getCreatedBy(),
                                                "Campaign: " + campaign.getName())))))
                .onErrorResume(org.springframework.dao.DataIntegrityViolationException.class, e -> {
                    log.info("Campaign trial not granted to organisation {}: owner already received one", org.getId());
                    return Mono.empty();
                });
    }

    // ── internals ─────────────────────────────────────────────────────────────────

    private Mono<TrialGrant> createGrant(Organisation org, TrialSource source, UUID campaignId, int days,
                                         Long grantedBy, String reason) {
        LocalDateTime now = LocalDateTime.now();
        TrialGrant g = new TrialGrant();
        g.setOrganisationId(org.getId());
        g.setOwnerClientId(org.getClientId());
        g.setSource(source);
        g.setCampaignId(campaignId);
        g.setStatus(TrialGrantStatus.ACTIVE);
        g.setDurationDays(days);
        g.setStartsAt(now);
        g.setEndsAt(now.plusDays(days));
        g.setGrantedBy(grantedBy);
        g.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        g.setCreatedAt(now);
        g.setUpdatedAt(now);
        return grantRepo.save(g)
                .flatMap(saved -> event(saved, TrialEventType.GRANTED, null, saved.getEndsAt(), grantedBy, reason,
                        source == TrialSource.CAMPAIGN ? "campaign " + campaignId : null).thenReturn(saved));
    }

    /** The organisation's trial that is giving access now (an open grant whose end hasn't passed). */
    private Mono<TrialGrant> currentGrant(UUID organisationId) {
        LocalDateTime now = LocalDateTime.now();
        return grantRepo.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE)
                .filter(g -> g.isCurrent(now))
                .switchIfEmpty(Mono.error(new InvalidRecord("This organisation has no active trial")));
    }

    /** An open grant whose end has passed is marked ENDED, so a new grant can be made. */
    private Mono<Void> closeExpiredOpenGrant(UUID organisationId, Long adminId) {
        LocalDateTime now = LocalDateTime.now();
        return grantRepo.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE)
                .filter(g -> !now.isBefore(g.getEndsAt()))
                .flatMap(g -> {
                    g.setStatus(TrialGrantStatus.ENDED);
                    g.setUpdatedAt(now);
                    return grantRepo.save(g)
                            .flatMap(saved -> event(saved, TrialEventType.ENDED, saved.getEndsAt(), saved.getEndsAt(),
                                    adminId, null, "closed before a new grant"));
                })
                .then();
    }

    /**
     * Shifts paid periods that haven't started yet and start at or after the trial's old end by {@code delta},
     * keeping their length, so paid time keeps starting exactly when the trial ends.
     */
    private Mono<Void> moveUnstartedPaidPeriods(TrialGrant grant, LocalDateTime oldTrialEnd, Duration delta,
                                                Long adminId, String reason) {
        if (delta.isZero()) return Mono.empty();
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(
                        grant.getOrganisationId(), SubscriptionStatus.ACTIVE)
                .filter(s -> s.getStartDate() != null && s.getStartDate().isAfter(now)
                        && !s.getStartDate().isBefore(oldTrialEnd))
                .concatMap(s -> {
                    s.setStartDate(s.getStartDate().plus(delta));
                    s.setEndDate(s.getEndDate().plus(delta));
                    s.setUpdatedAt(now);
                    return subscriptionRepo.save(s);
                })
                .collectList()
                .flatMap(moved -> moved.isEmpty() ? Mono.empty()
                        : event(grant, TrialEventType.PAID_PERIODS_MOVED, oldTrialEnd, grant.getEndsAt(), adminId, reason,
                                moved.size() + " paid period(s) moved by " + delta.toHours() + " hour(s)"));
    }

    private Mono<Void> event(TrialGrant grant, TrialEventType type, LocalDateTime oldEndsAt, LocalDateTime newEndsAt,
                             Long adminId, String reason, String details) {
        TrialGrantEvent e = new TrialGrantEvent();
        e.setGrantId(grant.getId());
        e.setOrganisationId(grant.getOrganisationId());
        e.setEventType(type);
        e.setOldEndsAt(oldEndsAt);
        e.setNewEndsAt(newEndsAt);
        e.setAdminUserId(adminId);
        e.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        e.setDetails(details);
        e.setCreatedAt(LocalDateTime.now());
        return eventRepo.save(e).then();
    }

    private Mono<User> admin(String email) {
        return userRepo.findByUsername(email).switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")));
    }

    private Mono<Organisation> organisation(UUID organisationId) {
        return organisationRepo.findById(organisationId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")));
    }

    static Map<String, Object> summary(TrialGrant g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("grantId", g.getId());
        m.put("source", g.getSource());
        m.put("status", g.getStatus());
        m.put("durationDays", g.getDurationDays());
        m.put("startsAt", g.getStartsAt());
        m.put("endsAt", g.getEndsAt());
        return m;
    }

}
