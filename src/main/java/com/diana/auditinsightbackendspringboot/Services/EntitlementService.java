package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.AccessBasis;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single decision on whether an organisation can use its workspace, used by every access check and by the
 * access endpoint the app reads. AuditInsight is paid by default; access comes, in order of precedence, from:
 * <ol>
 *   <li>a current paid period ({@code ACTIVE} subscription with {@code startDate <= now < endDate});</li>
 *   <li>a current trial grant explicitly authorised by a Super Admin (individually or via a campaign);</li>
 *   <li>the platform-wide emergency override;</li>
 * </ol>
 * and otherwise payment is required. Old automatic TRIAL subscription rows grant nothing. Everything is decided
 * from stored dates at the moment of the check, so nothing has to be "switched on" or "switched off" on time.
 */
@Service
public class EntitlementService {

    /** The access decision plus what the app needs to explain it. */
    public record Entitlement(
            AccessBasis basis,
            /* When the access described by basis ends (null for PAYMENT_REQUIRED). */
            LocalDateTime accessUntil,
            /* The paid period running now, if any. */
            Subscription currentPaid,
            /* The next paid period that hasn't started yet (e.g. bought during a trial), if any. */
            Subscription nextPaid,
            /* The open trial grant, current or already expired, if any. */
            TrialGrant trial,
            /* When the emergency override ends, if it is active. */
            LocalDateTime emergencyUntil) {

        public boolean hasAccess() {
            return basis != AccessBasis.PAYMENT_REQUIRED;
        }
    }

    private final SubscriptionRepository subscriptionRepo;
    private final TrialGrantRepository trialGrantRepo;
    private final PlatformFreeAccessService platformFreeAccessService;

    public EntitlementService(SubscriptionRepository subscriptionRepo,
                              TrialGrantRepository trialGrantRepo,
                              PlatformFreeAccessService platformFreeAccessService) {
        this.subscriptionRepo = subscriptionRepo;
        this.trialGrantRepo = trialGrantRepo;
        this.platformFreeAccessService = platformFreeAccessService;
    }

    public Mono<Entitlement> resolve(UUID organisationId) {
        LocalDateTime now = LocalDateTime.now();
        return Mono.zip(
                        subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(
                                organisationId, SubscriptionStatus.ACTIVE).collectList(),
                        trialGrantRepo.findFirstByOrganisationIdAndStatus(organisationId, TrialGrantStatus.ACTIVE)
                                .map(Optional::of).defaultIfEmpty(Optional.empty()),
                        platformFreeAccessService.activeUntil())
                .map(t -> decide(now, t.getT1(), t.getT2().orElse(null), t.getT3().orElse(null)));
    }

    static Entitlement decide(LocalDateTime now, List<Subscription> activePeriods, TrialGrant openTrial,
                              LocalDateTime emergencyUntil) {
        Subscription currentPaid = activePeriods.stream()
                .filter(s -> isCurrent(s, now)).findFirst().orElse(null);
        Subscription nextPaid = activePeriods.stream()
                .filter(s -> s.getStartDate() != null && s.getStartDate().isAfter(now))
                .min((a, b) -> a.getStartDate().compareTo(b.getStartDate())).orElse(null);

        if (currentPaid != null) {
            return new Entitlement(AccessBasis.PAID, paidUntil(activePeriods, currentPaid), currentPaid, nextPaid,
                    openTrial, emergencyUntil);
        }
        if (openTrial != null && openTrial.isCurrent(now)) {
            return new Entitlement(AccessBasis.TRIAL, openTrial.getEndsAt(), null, nextPaid, openTrial, emergencyUntil);
        }
        if (emergencyUntil != null) {
            return new Entitlement(AccessBasis.EMERGENCY_OVERRIDE, emergencyUntil, null, nextPaid, openTrial,
                    emergencyUntil);
        }
        return new Entitlement(AccessBasis.PAYMENT_REQUIRED, null, null, nextPaid, openTrial, null);
    }

    static boolean isCurrent(Subscription s, LocalDateTime now) {
        return s.getEndDate() != null && now.isBefore(s.getEndDate())
                && (s.getStartDate() == null || !now.isBefore(s.getStartDate()));
    }

    /** End of continuous paid access: follows back-to-back paid periods from the current one. */
    private static LocalDateTime paidUntil(List<Subscription> activePeriods, Subscription current) {
        LocalDateTime until = current.getEndDate();
        boolean extended = true;
        while (extended) {
            extended = false;
            for (Subscription s : activePeriods) {
                if (s.getStartDate() != null && !s.getStartDate().isAfter(until) && s.getEndDate().isAfter(until)) {
                    until = s.getEndDate();
                    extended = true;
                }
            }
        }
        return until;
    }
}
