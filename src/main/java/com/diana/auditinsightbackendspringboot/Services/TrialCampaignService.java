package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Admin.AdminAuditService;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.TrialCampaign;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.TrialCampaignRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Super Admin-run free-trial campaigns for NEW organisations. A campaign only authorises grants: while it is enabled
 * and within its window, an eligible organisation registering gets a {@link TrialGrant} of the campaign's length.
 * It never gives access by itself, never grants existing organisations, and disabling it leaves grants already
 * issued untouched. Only one campaign can be enabled at a time.
 */
@Service
@Slf4j
public class TrialCampaignService {

    /** A campaign plus how many trials it has granted so far. */
    public record CampaignView(TrialCampaign campaign, long grantsIssued, boolean grantingNow) {}

    private final TrialCampaignRepository campaignRepo;
    private final TrialGrantRepository grantRepo;
    private final TrialGrantService trialGrantService;
    private final UserRepository userRepo;
    private final AdminAuditService adminAuditService;

    public TrialCampaignService(TrialCampaignRepository campaignRepo,
                                TrialGrantRepository grantRepo,
                                TrialGrantService trialGrantService,
                                UserRepository userRepo,
                                AdminAuditService adminAuditService) {
        this.campaignRepo = campaignRepo;
        this.grantRepo = grantRepo;
        this.trialGrantService = trialGrantService;
        this.userRepo = userRepo;
        this.adminAuditService = adminAuditService;
    }

    // ── Super Admin actions ───────────────────────────────────────────────────────

    /** Creates a campaign, disabled. {@code startsAt} defaults to now; {@code endsAt} is optional. */
    public Mono<CampaignView> create(String name, int durationDays, LocalDateTime startsAt, LocalDateTime endsAt,
                                     String reason, String adminEmail) {
        if (name == null || name.isBlank()) {
            return Mono.error(new InvalidRecord("A campaign name is required"));
        }
        if (durationDays < 1 || durationDays > TrialGrantService.MAX_TRIAL_DAYS) {
            return Mono.error(new InvalidRecord("Campaign trials must be between 1 and "
                    + TrialGrantService.MAX_TRIAL_DAYS + " days"));
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = startsAt == null ? now : startsAt;
        if (endsAt != null && !endsAt.isAfter(start)) {
            return Mono.error(new InvalidRecord("The campaign end must be after its start"));
        }
        return admin(adminEmail).flatMap(admin -> {
            TrialCampaign c = new TrialCampaign();
            c.setName(name.trim());
            c.setDurationDays(durationDays);
            c.setStartsAt(start);
            c.setEndsAt(endsAt);
            c.setEnabled(false);
            c.setCreatedBy(admin.getId());
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            return campaignRepo.save(c)
                    .flatMap(saved -> adminAuditService.record(admin.getId(), "CREATE_TRIAL_CAMPAIGN", "TRIAL_CAMPAIGN",
                                    saved.getId().toString(), null, summary(saved), reason)
                            .then(view(saved)));
        });
    }

    /** Enables a campaign. Fails if another campaign is already enabled (disable it first). */
    public Mono<CampaignView> enable(UUID campaignId, String reason, String adminEmail) {
        return setEnabled(campaignId, true, reason, adminEmail);
    }

    /** Disables a campaign: no further grants. Trials already granted keep running until their own end date. */
    public Mono<CampaignView> disable(UUID campaignId, String reason, String adminEmail) {
        return setEnabled(campaignId, false, reason, adminEmail);
    }

    public Flux<CampaignView> list() {
        return campaignRepo.findAllByOrderByCreatedAtDesc().concatMap(this::view);
    }

    // ── Registration ──────────────────────────────────────────────────────────────

    /**
     * Called when an organisation registers: grants the enabled campaign's trial if it is within its window and the
     * owner hasn't had a campaign trial before. Empty when no trial applies — registration itself never fails here.
     */
    public Mono<TrialGrant> grantOnRegistration(Organisation org) {
        LocalDateTime now = LocalDateTime.now();
        return campaignRepo.findFirstByEnabledTrue()
                .filter(c -> c.isGranting(now))
                .flatMap(c -> trialGrantService.grantFromCampaign(org, c))
                .onErrorResume(e -> {
                    log.error("Could not apply the trial campaign to organisation {}: {}", org.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    // ── internals ─────────────────────────────────────────────────────────────────

    private Mono<CampaignView> setEnabled(UUID campaignId, boolean enabled, String reason, String adminEmail) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new InvalidRecord("A reason is required to " + (enabled ? "enable" : "disable") + " a campaign"));
        }
        return admin(adminEmail).flatMap(admin -> campaignRepo.findById(campaignId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Campaign not found")))
                .flatMap(c -> {
                    if (c.isEnabled() == enabled) {
                        return Mono.error(new InvalidRecord("This campaign is already " + (enabled ? "enabled" : "disabled")));
                    }
                    if (enabled && c.getEndsAt() != null && !c.getEndsAt().isAfter(LocalDateTime.now())) {
                        return Mono.error(new InvalidRecord("This campaign has already ended"));
                    }
                    Map<String, Object> before = summary(c);
                    c.setEnabled(enabled);
                    c.setUpdatedAt(LocalDateTime.now());
                    return campaignRepo.save(c)
                            .onErrorMap(DataIntegrityViolationException.class, e -> new InvalidRecord(
                                    "Another campaign is already enabled. Disable it first."))
                            .flatMap(saved -> adminAuditService.record(admin.getId(),
                                            enabled ? "ENABLE_TRIAL_CAMPAIGN" : "DISABLE_TRIAL_CAMPAIGN",
                                            "TRIAL_CAMPAIGN", saved.getId().toString(), before, summary(saved), reason)
                                    .then(view(saved)));
                }));
    }

    private Mono<CampaignView> view(TrialCampaign c) {
        return grantRepo.countByCampaignId(c.getId()).defaultIfEmpty(0L)
                .map(count -> new CampaignView(c, count, c.isGranting(LocalDateTime.now())));
    }

    private Mono<User> admin(String email) {
        return userRepo.findByUsername(email).switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")));
    }

    static Map<String, Object> summary(TrialCampaign c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("campaignId", c.getId());
        m.put("name", c.getName());
        m.put("durationDays", c.getDurationDays());
        m.put("startsAt", c.getStartsAt());
        m.put("endsAt", c.getEndsAt());
        m.put("enabled", c.isEnabled());
        return m;
    }
}
