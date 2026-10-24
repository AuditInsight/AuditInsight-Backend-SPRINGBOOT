package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Repositories.PlanPriceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Single source of truth for subscription prices/durations, backed by the platform-admin-
 * editable {@code plan_prices} table. Falls back to {@link SubscriptionType}'s original
 * reference values if a row is somehow missing, so pricing never hard-fails checkout.
 */
@Service
public class PlanPriceService {

    private final PlanPriceRepository planPriceRepository;

    public PlanPriceService(PlanPriceRepository planPriceRepository) {
        this.planPriceRepository = planPriceRepository;
    }

    public Mono<PlanPrice> getPrice(SubscriptionType type) {
        return planPriceRepository.findById(type)
                .switchIfEmpty(Mono.just(fallback(type)));
    }

    public Flux<PlanPrice> listPrices() {
        return planPriceRepository.findAllByOrderBySubscriptionType();
    }

    private PlanPrice fallback(SubscriptionType type) {
        PlanPrice fallback = new PlanPrice();
        fallback.setSubscriptionType(type);
        fallback.setPrice(type.getPriceRwf());
        fallback.setCurrency(SubscriptionType.CURRENCY);
        fallback.setDurationDays(type.getDurationDays());
        return fallback;
    }
}
