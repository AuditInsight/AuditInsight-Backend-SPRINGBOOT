package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class SubscriptionExpiryScheduler {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final SubscriptionRepository subscriptionRepo;
    private final OrganisationRepository organisationRepo;
    private final OrganisationMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final TrialGrantRepository trialGrantRepo;

    public SubscriptionExpiryScheduler(SubscriptionRepository subscriptionRepo,
                                        OrganisationRepository organisationRepo,
                                        OrganisationMemberRepository memberRepo,
                                        UserRepository userRepo,
                                        EmailService emailService,
                                        TrialGrantRepository trialGrantRepo) {
        this.subscriptionRepo = subscriptionRepo;
        this.organisationRepo = organisationRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.trialGrantRepo = trialGrantRepo;
    }

    @Scheduled(fixedRate = 3_600_000) // every hour
    public void processSubscriptionLifecycle() {
        flipExpiredSubscriptions();
        sendExpiryReminders();
        sendTrialGrantReminders();
    }

    /** Housekeeping — access gating itself checks dates live, this just keeps the status column truthful. */
    private void flipExpiredSubscriptions() {
        subscriptionRepo.findByStatusInAndEndDateBefore(
                        List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE), LocalDateTime.now())
                .flatMap(sub -> {
                    sub.setStatus(SubscriptionStatus.EXPIRED);
                    sub.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRepo.save(sub);
                })
                .doOnError(e -> log.error("Failed to flip expired subscription: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * "Subscription ending" reminders for paid periods only. Old automatic TRIAL subscription rows never gave access,
     * so they no longer get "your trial is ending" emails; real trials are reminded from their grants instead
     * ({@link #sendTrialGrantReminders}). A period followed straight away by another paid period isn't really
     * ending, so it gets no reminder.
     */
    private void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.plusDays(7);
        LocalDateTime windowEnd = windowStart.plusHours(1);

        subscriptionRepo.findByStatusInAndEndDateBetween(List.of(SubscriptionStatus.ACTIVE), windowStart, windowEnd)
                .filter(sub -> !sub.isExpiryReminderSent())
                .filterWhen(sub -> subscriptionRepo.findAllByOrganisationIdAndStatusOrderByEndDateAsc(
                                sub.getOrganisationId(), SubscriptionStatus.ACTIVE)
                        .any(next -> next.getStartDate() != null && !next.getStartDate().isAfter(sub.getEndDate())
                                && next.getEndDate().isAfter(sub.getEndDate()))
                        .map(continues -> !continues))
                .flatMap(this::sendReminderAndMarkSent)
                .doOnError(e -> log.error("Failed to send expiry reminder: {}", e.getMessage()))
                .subscribe();
    }

    /** One "trial ending soon" email per Super Admin-authorised trial, three days before it ends. */
    private void sendTrialGrantReminders() {
        LocalDateTime now = LocalDateTime.now();
        trialGrantRepo.findAllByStatusAndEndsAtBetween(TrialGrantStatus.ACTIVE, now, now.plusDays(3))
                .filter(grant -> !grant.isReminderSent())
                .flatMap(grant -> resolveOwnerEmail(grant.getOrganisationId())
                        .flatMap(tuple -> {
                            long daysRemaining = Math.max(1, Duration.between(now, grant.getEndsAt()).toDays() + 1);
                            emailService.sendTrialExpiringReminderEmail(tuple.getT1(), tuple.getT2().getName(),
                                    daysRemaining, grant.getEndsAt().format(DISPLAY_DATE));
                            grant.setReminderSent(true);
                            grant.setUpdatedAt(now);
                            return trialGrantRepo.save(grant).then();
                        }))
                .doOnError(e -> log.error("Failed to send trial reminder: {}", e.getMessage()))
                .subscribe();
    }

    private Mono<Void> sendReminderAndMarkSent(Subscription sub) {
        return resolveOwnerEmail(sub.getOrganisationId())
                .flatMap(tuple -> {
                    String ownerEmail = tuple.getT1();
                    Organisation org = tuple.getT2();
                    long daysRemaining = Duration.between(LocalDateTime.now(), sub.getEndDate()).toDays() + 1;
                    String expiryDate = sub.getEndDate().format(DISPLAY_DATE);

                    String plan = sub.getSubscriptionType() == null ? "current" : sub.getSubscriptionType().name();
                    emailService.sendSubscriptionExpiringReminderEmail(
                            ownerEmail, org.getName(), plan, daysRemaining, expiryDate);
                    sub.setExpiryReminderSent(true);
                    sub.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRepo.save(sub).then();
                });
    }

    private Mono<Tuple2<String, Organisation>> resolveOwnerEmail(UUID organisationId) {
        return organisationRepo.findById(organisationId)
                .flatMap(org -> memberRepo.findAllByOrganisationId(organisationId)
                        .filter(m -> m.getRole() == Role.CLIENT && m.getStatus() == MemberStatus.ACTIVE)
                        .next()
                        .flatMap(owner -> userRepo.findById(owner.getUserId()).map(User::getUsername))
                        .map(email -> Tuples.of(email, org)));
    }
}
