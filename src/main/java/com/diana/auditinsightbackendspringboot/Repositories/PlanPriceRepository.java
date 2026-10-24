package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PlanPriceRepository extends ReactiveCrudRepository<PlanPrice, SubscriptionType> {
    Flux<PlanPrice> findAllByOrderBySubscriptionType();
}

