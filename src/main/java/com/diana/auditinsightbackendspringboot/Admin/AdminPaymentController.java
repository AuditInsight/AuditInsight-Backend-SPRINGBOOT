package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminPaymentResponse;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.DTOs.PaymentLedgerTotal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments")
@Tag(name = "Admin Payments", description = "Super Admin ledger of every payment on the platform")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    public AdminPaymentController(AdminPaymentService adminPaymentService) {
        this.adminPaymentService = adminPaymentService;
    }

    @GetMapping
    @Operation(summary = "List payments",
            description = "Every payment, newest first, 25 per page: organisation, payer (name, email, phone), plan, "
                    + "method, amount expected and received, operator reference, failure reason, and when it was "
                    + "started and settled. All filters are optional. 'from' and 'to' are ISO date-times with an offset "
                    + "(e.g. 2026-11-20T00:00:00+02:00) on when the payment was started; 'from' inclusive, 'to' exclusive.")
    public Mono<PageResult<AdminPaymentResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String search) {
        return adminPaymentService.list(
                new AdminPaymentService.Filters(status, provider, organisationId, from, to, search), page);
    }

    @GetMapping("/totals")
    @Operation(summary = "Payment totals",
            description = "For the same filters as the list: how many payments per status and currency, and how much "
                    + "money providers confirmed was received.")
    public Mono<List<PaymentLedgerTotal>> totals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String search) {
        return adminPaymentService.totals(
                new AdminPaymentService.Filters(status, provider, organisationId, from, to, search));
    }
}
