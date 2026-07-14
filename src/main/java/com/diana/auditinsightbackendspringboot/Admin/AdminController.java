package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only endpoints for managing account activation")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PatchMapping("/users/{email}/activate")
    @Operation(
            summary = "Activate account",
            description = "Activates a CLIENT, AUDITOR, or MEMBER account so it can log in again. Requires ADMIN role."
    )
    public Mono<ResponseEntity<ResponseMessage>> activateAccount(@PathVariable String email) {
        return adminService.activateAccount(email)
                .map(response -> new ResponseEntity<>(response, response.getStatus()));
    }

    @PatchMapping("/users/{email}/deactivate")
    @Operation(
            summary = "Deactivate account",
            description = "Deactivates a CLIENT, AUDITOR, or MEMBER account, blocking further logins. Requires ADMIN role."
    )
    public Mono<ResponseEntity<ResponseMessage>> deactivateAccount(@PathVariable String email) {
        return adminService.deactivateAccount(email)
                .map(response -> new ResponseEntity<>(response, response.getStatus()));
    }
}
