package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AccessResponse;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionSource;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.AccessStatusService;
import com.diana.auditinsightbackendspringboot.Services.EntitlementService;
import com.diana.auditinsightbackendspringboot.Services.OrganisationLock;
import com.diana.auditinsightbackendspringboot.Services.TrialGrantService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super Admin view and removal of one organisation's FREE access: a trial, or paid-period rows that no successful
 * payment bought (admin complimentary time, or legacy rows from before payments were tracked). Real paid time is
 * never touched, except that paid time queued after the removed free time moves earlier so none of it is lost.
 * The platform-wide emergency override is separate and applies to everyone.
 */
@Service
public class OrganisationFreeAccessService {

    /** A paid-period row not backed by a successful payment. */
    public record FreePeriod(UUID id, SubscriptionType subscriptionType, LocalDateTime startDate,
                             LocalDateTime endDate, SubscriptionSource source, boolean current) {}

    /** What the admin sees: the organisation's access decision plus any free access that can be ended. */
    public record AdminAccessView(AccessResponse access, boolean trialRunning, List<FreePeriod> freePeriods) {
        public boolean hasFreeAccess() {
            return trialRunning || !freePeriods.isEmpty();
        }
    }

    private final OrganisationRepository organisationRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final PaymentRepository paymentRepo;
    private final TrialGrantRepository trialGrantRepo;
    private final TrialGrantService trialGrantService;
    private final EntitlementService entitlementService;
    private final UserRepository userRepo;
    private final AdminAuditService adminAuditService;
    private final OrganisationLock organisationLock;

    public OrganisationFreeAccessService(OrganisationRepository organisationRepo,
                                         SubscriptionRepository subscriptionRepo,
                                         PaymentRepository paymentRepo,
                                         TrialGrantRepository trialGrantRepo,
                                         TrialGrantService trialGrantService,
                                         EntitlementService entitlementService,
                                         UserRepository userRepo,
                                         AdminAuditService adminAuditService,
                                         OrganisationLock organisationLock) {
        this.organisationRepo = organisationRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.paymentRepo = paymentRepo;
        this.trialGrantRepo = trialGrantRepo;
        this.trialGrantService = trialGrantService;
        this.entitlementService = entitlementService;
        this.userRepo = userRepo;
        this.adminAuditService = adminAuditService;
        this.organisationLock = organisationLock;
    }

    public Mono<AdminAccessView> view(UUID organisationId) {
        LocalDateTime now = LocalDateTime.now();
        return organisationRepo.findById(organisationId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                .flatMap(org -> Mono.zip(
                        entitlementService.resolve(organisationId),
                        trialRunning(organisationId, now),
                        unexpired(organisationId, now).filterWhen(this::isFree).collectList())
                        .map(t -> new AdminAccessView(
                                AccessStatusService.toResponse(t.getT1(), org.getRequestedSubscriptionType()),
                                t.getT2(),
                                t.getT3().stream().map(s -> toFree(s, now)).toList())));
    }

    /**
     * Ends all free access for one organisation now: revokes its running trial, ends the free period running now and
     * cancels free periods queued for later. Paid time queued afterwards is pulled forward so it starts straight away.
     */
    public Mono<AdminAccessView> endFreeAccess(UUID organisationId, String reason, String adminEmail) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new InvalidRecord("A reason is required to end an organisation's free access"));
        }
        String cleanReason = reason.trim();
        return userRepo.findByUsername(adminEmail)
                .switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")))
                .flatMap(admin -> view(organisationId).flatMap(before -> {
                    if (!before.hasFreeAccess()) {
                        return Mono.error(new InvalidRecord("This organisation has no free access to end"
                                + (before.access().basis().name().equals("EMERGENCY_OVERRIDE")
                                        ? ". Its only access is the platform-wide emergency override." : ".")));
                    }
                    return organisationLock.inTransaction(organisationId, () ->
                                    (before.trialRunning()
                                            ? trialGrantService.revoke(organisationId, cleanReason, adminEmail).then()
                                            : Mono.<Void>empty())
                                            .then(endFreePeriods(organisationId, cleanReason))
                                            .then(compactQueuedPaidTime(organisationId)))
                            .then(audit(admin, organisationId, before, cleanReason))
                            .then(view(organisationId));
                }));
    }

    // ── internals ─────────────────────────────────────────────────────────────────

    private Mono<Void> endFreePeriods(UUID organisationId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        return unexpired(organisationId, now)
                .filterWhen(this::isFree)
                .concatMap(s -> {
                    if (EntitlementService.isCurrentPeriod(s, now)) {
                        s.setEndDate(now);
                        s.setStatus(SubscriptionStatus.EXPIRED);
                    } else {
                        s.setStatus(SubscriptionStatus.CANCELLED);
                    }
                    s.setAdminReason(reason);
                    s.setUpdatedAt(now);
                    return subscriptionRepo.save(s);
                })
                .then();
    }

    /**
     * Makes the remaining paid periods back to back again: the first queued one starts when the running paid period
     * ends (or now if none is running), each later one when the previous ends. Periods only ever move earlier.
     */
    private Mono<Void> compactQueuedPaidTime(UUID organisationId) {
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(organisationId, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now))
                .collectList()
                .flatMap(periods -> {
                    LocalDateTime cursor = periods.stream()
                            .filter(s -> EntitlementService.isCurrentPeriod(s, now))
                            .map(Subscription::getEndDate)
                            .max(Comparator.naturalOrder())
                            .orElse(now);
                    List<Subscription> moved = new ArrayList<>();
                    List<Subscription> queued = periods.stream()
                            .filter(s -> s.getStartDate() != null && s.getStartDate().isAfter(now))
                            .sorted(Comparator.comparing(Subscription::getStartDate))
                            .toList();
                    for (Subscription s : queued) {
                        if (s.getStartDate().isAfter(cursor)) {
                            Duration delta = Duration.between(s.getStartDate(), cursor);
                            s.setStartDate(cursor);
                            s.setEndDate(s.getEndDate().plus(delta));
                            s.setUpdatedAt(now);
                            moved.add(s);
                        }
                        cursor = s.getEndDate();
                    }
                    return Flux.fromIterable(moved).concatMap(subscriptionRepo::save).then();
                });
    }

    private Mono<Boolean> trialRunning(UUID organisationId, LocalDateTime now) {
        return trialGrantRepo.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE)
                .map(g -> g.isCurrent(now))
                .defaultIfEmpty(false);
    }

    /** ACTIVE periods that haven't ended yet (running now or queued). */
    private Flux<Subscription> unexpired(UUID organisationId, LocalDateTime now) {
        return subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(organisationId, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now));
    }

    private Mono<Boolean> isFree(Subscription s) {
        return paymentRepo.existsBySubscriptionIdAndStatus(s.getId(), PaymentStatus.SUCCESSFUL).map(paid -> !paid);
    }

    private static FreePeriod toFree(Subscription s, LocalDateTime now) {
        return new FreePeriod(s.getId(), s.getSubscriptionType(), s.getStartDate(), s.getEndDate(), s.getSource(),
                EntitlementService.isCurrentPeriod(s, now));
    }

    private Mono<Void> audit(User admin, UUID organisationId, AdminAccessView before, String reason) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("basis", before.access().basis());
        state.put("trialRunning", before.trialRunning());
        state.put("freePeriods", before.freePeriods());
        return adminAuditService.record(admin.getId(), "END_ORGANISATION_FREE_ACCESS", "ORGANISATION",
                organisationId.toString(), state, Map.of("freeAccessEnded", true), reason).then();
    }
}
