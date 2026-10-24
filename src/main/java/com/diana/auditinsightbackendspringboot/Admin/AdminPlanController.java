package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminPlanPriceResponse;
import com.diana.auditinsightbackendspringboot.DTOs.AdminUpdatePlanPriceRequest;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/plans")
@Tag(name = "Admin Plans", description = "Platform-admin editing of subscription plan prices/durations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlanController {

    private final AdminPlanService adminPlanService;

    public AdminPlanController(AdminPlanService adminPlanService) {
        this.adminPlanService = adminPlanService;
    }

    @GetMapping
    @Operation(summary = "List plan prices", description = "Lists the 3 fixed subscription periods with their current admin-editable price/duration.")
    public Flux<AdminPlanPriceResponse> list() {
        return adminPlanService.listPlans();
    }

    @PutMapping("/{subscriptionType}")
    @Operation(summary = "Update a plan price", description = "Updates the price and/or duration of one subscription period.")
    public Mono<AdminPlanPriceResponse> update(
            Authentication authentication,
            @PathVariable SubscriptionType subscriptionType,
            @Valid @RequestBody AdminUpdatePlanPriceRequest request) {
        return adminPlanService.updatePrice(subscriptionType, request, authentication.getName());
    }
}
