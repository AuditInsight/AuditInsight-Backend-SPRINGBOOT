package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminSubscriptionSummary;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/subscriptions")
@Tag(name = "Admin Subscriptions", description = "Platform-admin view of all subscriptions across organisations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    public AdminSubscriptionController(AdminSubscriptionService adminSubscriptionService) {
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping
    @Operation(summary = "List subscriptions", description = "Paginated list of subscriptions across all organisations, optionally filtered by status.")
    public Mono<PageResult<AdminSubscriptionSummary>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) SubscriptionStatus status) {
        return adminSubscriptionService.listSubscriptions(page, status);
    }
}
