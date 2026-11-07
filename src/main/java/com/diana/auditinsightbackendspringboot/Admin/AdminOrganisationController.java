package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminChangeSubscriptionRequest;
import com.diana.auditinsightbackendspringboot.DTOs.AdminOrganisationDetailResponse;
import com.diana.auditinsightbackendspringboot.DTOs.AdminOrganisationResponse;
import com.diana.auditinsightbackendspringboot.DTOs.AdminSubscriptionSummary;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/organisations")
@Tag(name = "Admin Organisations", description = "Platform-admin view of all organisations, their members and subscriptions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrganisationController {

    private final AdminOrganisationService adminOrganisationService;
    private final OrganisationFreeAccessService freeAccessService;

    public AdminOrganisationController(AdminOrganisationService adminOrganisationService,
                                       OrganisationFreeAccessService freeAccessService) {
        this.adminOrganisationService = adminOrganisationService;
        this.freeAccessService = freeAccessService;
    }

    @GetMapping("/{organisationId}/access")
    @Operation(summary = "Organisation access", description = "Why the organisation can (or cannot) use its workspace, "
            + "plus any free access (trial, or periods no payment bought) that a Super Admin can end.")
    public Mono<OrganisationFreeAccessService.AdminAccessView> access(@PathVariable UUID organisationId) {
        return freeAccessService.view(organisationId);
    }

    @PostMapping("/{organisationId}/free-access/end")
    @Operation(summary = "End this organisation's free access", description = "Revokes its trial and ends periods no "
            + "payment bought, now. Real paid time is kept and starts straight away. A reason is required.")
    public Mono<OrganisationFreeAccessService.AdminAccessView> endFreeAccess(
            Authentication authentication,
            @PathVariable UUID organisationId,
            @Valid @RequestBody com.diana.auditinsightbackendspringboot.DTOs.TrialRequests.Reason body) {
        return freeAccessService.endFreeAccess(organisationId, body.reason(), authentication.getName());
    }

    @GetMapping
    @Operation(summary = "List organisations", description = "Paginated list of all organisations with member count and current subscription.")
    public Mono<PageResult<AdminOrganisationResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String search) {
        return adminOrganisationService.listOrganisations(page, search);
    }

    @GetMapping("/{organisationId}")
    @Operation(summary = "Get organisation detail", description = "Members, subscription history and payment history for one organisation.")
    public Mono<AdminOrganisationDetailResponse> getDetail(@PathVariable UUID organisationId) {
        return adminOrganisationService.getOrganisationDetail(organisationId);
    }

    @PatchMapping("/{organisationId}/subscription")
    @Operation(summary = "Change subscription tier", description = "Sets the organisation's current subscription type/end date.")
    public Mono<AdminSubscriptionSummary> changeSubscription(
            Authentication authentication,
            @PathVariable UUID organisationId,
            @Valid @RequestBody AdminChangeSubscriptionRequest request) {
        return adminOrganisationService.changeSubscription(organisationId, request, authentication.getName());
    }
}
