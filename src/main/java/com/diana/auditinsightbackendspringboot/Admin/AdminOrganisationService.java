package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import com.diana.auditinsightbackendspringboot.Services.SubscriptionActivationService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Service
public class AdminOrganisationService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final OrganisationRepository organisationRepository;
    private final OrganisationMemberRepository organisationMemberRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SubscriptionActivationService activationService;
    private final AdminAuditService adminAuditService;

    public AdminOrganisationService(OrganisationRepository organisationRepository,
                                     OrganisationMemberRepository organisationMemberRepository,
                                     SubscriptionRepository subscriptionRepository,
                                     PaymentRepository paymentRepository,
                                     UserRepository userRepository,
                                     SubscriptionActivationService activationService,
                                     AdminAuditService adminAuditService) {
        this.organisationRepository = organisationRepository;
        this.organisationMemberRepository = organisationMemberRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.activationService = activationService;
        this.adminAuditService = adminAuditService;
    }

    public Mono<PageResult<AdminOrganisationResponse>> listOrganisations(int page, String search) {
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * DEFAULT_PAGE_SIZE;
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        return organisationRepository.findPage(normalizedSearch, DEFAULT_PAGE_SIZE, offset)
                .flatMap(this::toListResponse)
                .collectList()
                .zipWith(organisationRepository.countPage(normalizedSearch))
                .map(tuple -> PageResult.of(tuple.getT1(), tuple.getT2(), safePage, DEFAULT_PAGE_SIZE));
    }

    private Mono<AdminOrganisationResponse> toListResponse(Organisation organisation) {
        Mono<Long> memberCountMono = organisationMemberRepository.countByOrganisationId(organisation.getId());
        Mono<Optional<Subscription>> subscriptionMono = subscriptionRepository
                .findFirstByOrganisationIdOrderByCreatedAtDesc(organisation.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return memberCountMono.zipWith(subscriptionMono).map(tuple -> {
            long memberCount = tuple.getT1();
            Subscription sub = tuple.getT2().orElse(null);
            return new AdminOrganisationResponse(
                    organisation.getId(),
                    organisation.getName(),
                    organisation.getIndustry(),
                    organisation.getCountryCode() == null ? null : organisation.getCountryCode().toString(),
                    organisation.getDefaultCurrency(),
                    organisation.getCreatedAt(),
                    memberCount,
                    sub == null ? null : sub.getStatus(),
                    sub == null ? null : sub.getSubscriptionType(),
                    sub == null ? null : sub.getEndDate());
        });
    }

    public Mono<AdminOrganisationDetailResponse> getOrganisationDetail(UUID organisationId) {
        return organisationRepository.findById(organisationId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                .flatMap(organisation -> Mono.zip(
                        membersFor(organisationId).collectList(),
                        subscriptionRepository.findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                                .map(this::toSubscriptionSummary)
                                .collectList(),
                        paymentRepository.findAllByOrganisationIdOrderByCreatedAtDesc(organisationId)
                                .map(this::toPaymentResponse)
                                .collectList()
                ).map(tuple -> new AdminOrganisationDetailResponse(
                        organisation.getId(),
                        organisation.getName(),
                        organisation.getIndustry(),
                        organisation.getCountryCode() == null ? null : organisation.getCountryCode().toString(),
                        organisation.getDefaultCurrency(),
                        organisation.getCreatedAt(),
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3())));
    }

    private Flux<AdminMemberResponse> membersFor(UUID organisationId) {
        return organisationMemberRepository.findAllByOrganisationId(organisationId)
                .flatMap(member -> userRepository.findById(member.getUserId())
                        .map(user -> toMemberResponse(member, user)));
    }

    private AdminMemberResponse toMemberResponse(OrganisationMember member, User user) {
        return new AdminMemberResponse(user.getId(), user.getUsername(), user.getFullName(),
                member.getRole(), member.getStatus(), member.getJoinedAt());
    }

    private AdminSubscriptionSummary toSubscriptionSummary(Subscription subscription) {
        return new AdminSubscriptionSummary(subscription.getId(), subscription.getOrganisationId(), null,
                subscription.getSubscriptionType(), subscription.getStatus(), subscription.getStartDate(),
                subscription.getEndDate(), subscription.getCreatedAt());
    }

    private AdminPaymentResponse toPaymentResponse(Payment payment) {
        return new AdminPaymentResponse(payment.getId(), payment.getSubscriptionType(), payment.getProvider(),
                payment.getStatus(), payment.getExpectedAmount(), payment.getReceivedAmount(),
                payment.getCurrency(), payment.getCreatedAt());
    }

    public Mono<AdminSubscriptionSummary> changeSubscription(UUID organisationId, AdminChangeSubscriptionRequest request,
                                                              String actingAdminUsername) {
        return organisationRepository.findById(organisationId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                .flatMap(organisation -> userRepository.findByUsername(actingAdminUsername)
                        .switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")))
                        .flatMap(admin -> subscriptionRepository
                                .findByOrganisationIdAndStatus(organisationId, SubscriptionStatus.ACTIVE)
                                .map(this::toSubscriptionSummary)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty())
                                .flatMap(beforeOpt -> activationService.adminChangeSubscription(
                                                organisationId, request.getSubscriptionType(), request.getEndDate(), admin.getId())
                                        .flatMap(after -> {
                                            AdminSubscriptionSummary afterSummary = toSubscriptionSummary(after);
                                            return adminAuditService.record(admin.getId(), "CHANGE_SUBSCRIPTION",
                                                            "ORGANISATION", organisationId.toString(),
                                                            beforeOpt.orElse(null), afterSummary)
                                                    .thenReturn(afterSummary);
                                        }))));
    }
}
