package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.PlanResponse;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Services.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Plans", description = "Fixed-price subscription catalog")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "List subscription plans",
               description = "Lists the 3 fixed-price RWF subscription periods (Monthly/6 Months/Annual).")
    public Flux<PlanResponse> list() {
        return planService.listPlans().map(this::toResponse);
    }

    private PlanResponse toResponse(PlanPrice price) {
        return new PlanResponse(price.getSubscriptionType(), price.getDurationDays(), price.getPrice(), price.getCurrency());
    }
}
