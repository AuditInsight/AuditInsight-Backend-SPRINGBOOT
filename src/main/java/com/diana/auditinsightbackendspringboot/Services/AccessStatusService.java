package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.AccessResponse;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/** Builds the access picture shown to an organisation's members (paywall, banners, billing page). */
@Service
public class AccessStatusService {

    private final OrgAccessService orgAccessService;
    private final EntitlementService entitlementService;
    private final OrganisationRepository organisationRepo;

    public AccessStatusService(OrgAccessService orgAccessService,
                               EntitlementService entitlementService,
                               OrganisationRepository organisationRepo) {
        this.orgAccessService = orgAccessService;
        this.entitlementService = entitlementService;
        this.organisationRepo = organisationRepo;
    }

    /** Any active member may ask; membership only, not gated (the paywall itself needs this). */
    public Mono<AccessResponse> forMember(UUID organisationId, String email) {
        return orgAccessService.resolveActiveMember(organisationId, email)
                .then(Mono.zip(entitlementService.resolve(organisationId), organisationRepo.findById(organisationId)))
                .map(t -> toResponse(t.getT1(), t.getT2().getRequestedSubscriptionType()));
    }

    public static AccessResponse toResponse(EntitlementService.Entitlement e,
                                     SubscriptionType requestedPlan) {
        LocalDateTime now = LocalDateTime.now();
        return new AccessResponse(e.hasAccess(), e.basis(), e.accessUntil(), period(e.currentPaid()), period(e.nextPaid()),
                trial(e.trial(), now), e.emergencyUntil(), requestedPlan);
    }

    private static AccessResponse.PaidPeriod period(Subscription s) {
        return s == null ? null
                : new AccessResponse.PaidPeriod(s.getSubscriptionType(), s.getStartDate(), s.getEndDate(), s.getSource());
    }

    private static AccessResponse.Trial trial(TrialGrant g, LocalDateTime now) {
        return g == null ? null
                : new AccessResponse.Trial(g.getSource(), g.getDurationDays(), g.getStartsAt(), g.getEndsAt(),
                        g.isCurrent(now));
    }
}
