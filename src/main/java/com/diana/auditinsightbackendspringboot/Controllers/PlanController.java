package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.PlanResponse;
import com.diana.auditinsightbackendspringboot.Models.Plan;
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
@Tag(name = "Plans", description = "Subscription plan catalog")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "List plans",
               description = "Lists all available subscription plans with their USD pricing and feature sets.")
    public Flux<PlanResponse> list() {
        return planService.listPlans().map(this::toResponse);
    }

    private PlanResponse toResponse(Plan plan) {
        PlanResponse r = new PlanResponse();
        r.setTier(plan.getTier());
        r.setDescription(plan.getDescription());
        r.setMonthlyPriceUsd(plan.getMonthlyPriceUsd());
        r.setYearlyPriceUsd(plan.getYearlyPriceUsd());
        r.setMaxUsers(plan.getMaxUsers());
        r.setAuditsPerMonth(plan.getAuditsPerMonth());
        r.setStorageGb(plan.getStorageGb());
        r.setBasicReports(plan.isBasicReports());
        r.setEmailSupport(plan.isEmailSupport());
        r.setAdvancedReports(plan.isAdvancedReports());
        r.setAuditTrail(plan.isAuditTrail());
        r.setPrioritySupport(plan.isPrioritySupport());
        r.setCustomWorkflows(plan.isCustomWorkflows());
        r.setComplianceTemplates(plan.isComplianceTemplates());
        r.setApiAccess(plan.isApiAccess());
        r.setSupport247(plan.isSupport247());
        r.setSsoSaml(plan.isSsoSaml());
        r.setDedicatedAccountManager(plan.isDedicatedAccountManager());
        r.setCustomIntegrations(plan.isCustomIntegrations());
        r.setSlaGuarantee(plan.isSlaGuarantee());
        return r;
    }
}
