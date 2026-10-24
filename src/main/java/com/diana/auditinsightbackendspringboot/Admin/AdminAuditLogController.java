package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminAuditLogResponse;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
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
@RequestMapping("/api/admin/audit-log")
@Tag(name = "Admin Audit Log", description = "Read-only trail of platform-admin mutations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    public AdminAuditLogController(AdminAuditLogService adminAuditLogService) {
        this.adminAuditLogService = adminAuditLogService;
    }

    @GetMapping
    @Operation(summary = "List audit log entries", description = "Paginated, most-recent-first list of platform-admin mutations.")
    public Mono<PageResult<AdminAuditLogResponse>> list(@RequestParam(defaultValue = "1") int page) {
        return adminAuditLogService.listAuditLog(page);
    }
}
