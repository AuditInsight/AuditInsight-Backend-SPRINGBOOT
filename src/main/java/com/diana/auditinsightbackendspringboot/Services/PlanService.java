package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class PlanService {

    private final PlanPriceService planPriceService;

    public PlanService(PlanPriceService planPriceService) {
        this.planPriceService = planPriceService;
    }

    /** The 3 fixed subscription periods, with admin-editable price/duration from {@code plan_prices}. */
    public Flux<PlanPrice> listPlans() {
        return planPriceService.listPrices();
    }
}
