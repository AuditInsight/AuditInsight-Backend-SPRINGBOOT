package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only endpoints for managing auditor account approvals")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/auditors/pending")
    @Operation(
            summary = "List pending auditors",
            description = "Returns all auditor accounts that have registered but are not yet approved. Requires ADMIN role."
    )
    public Flux<AuditorProfile> getPendingAuditors() {
        return adminService.getPendingAuditors();
    }

    @PatchMapping("/auditors/{email}/approve")
    @Operation(
            summary = "Approve auditor account",
            description = "Activates an auditor account so they can log in. The auditor will be notified by email. Requires ADMIN role."
    )
    public Mono<ResponseEntity<ResponseMessage>> approveAuditor(@PathVariable String email) {
        return adminService.approveAuditor(email)
                .map(response -> new ResponseEntity<>(response, response.getStatus()));
    }
}
