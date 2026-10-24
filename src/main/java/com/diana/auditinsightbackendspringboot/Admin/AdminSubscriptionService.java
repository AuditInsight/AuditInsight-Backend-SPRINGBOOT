package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminSubscriptionSummary;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AdminSubscriptionService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SubscriptionRepository subscriptionRepository;
    private final OrganisationRepository organisationRepository;

    public AdminSubscriptionService(SubscriptionRepository subscriptionRepository,
                                     OrganisationRepository organisationRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.organisationRepository = organisationRepository;
    }

    public Mono<PageResult<AdminSubscriptionSummary>> listSubscriptions(int page, SubscriptionStatus status) {
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * DEFAULT_PAGE_SIZE;

        return subscriptionRepository.findPage(status, DEFAULT_PAGE_SIZE, offset)
                .flatMap(this::toSummary)
                .collectList()
                .zipWith(subscriptionRepository.countPage(status))
                .map(tuple -> PageResult.of(tuple.getT1(), tuple.getT2(), safePage, DEFAULT_PAGE_SIZE));
    }

    private Mono<AdminSubscriptionSummary> toSummary(Subscription subscription) {
        return organisationRepository.findById(subscription.getOrganisationId())
                .map(Organisation::getName)
                .defaultIfEmpty("Unknown organisation")
                .map(name -> new AdminSubscriptionSummary(subscription.getId(), subscription.getOrganisationId(), name,
                        subscription.getSubscriptionType(), subscription.getStatus(), subscription.getStartDate(),
                        subscription.getEndDate(), subscription.getCreatedAt()));
    }
}
