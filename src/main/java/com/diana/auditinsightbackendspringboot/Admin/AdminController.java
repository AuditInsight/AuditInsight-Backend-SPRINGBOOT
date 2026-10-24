package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminUserResponse;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only endpoints for managing accounts and viewing platform users")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final AdminUserService adminUserService;

    public AdminController(AdminService adminService, AdminUserService adminUserService) {
        this.adminService = adminService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    @Operation(
            summary = "List platform users",
            description = "Paginated list of all users, optionally filtered by role or a username/full-name search."
    )
    public Mono<PageResult<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search) {
        return adminUserService.listUsers(page, role, search);
    }

    @PatchMapping("/users/{email}/activate")
    @Operation(
            summary = "Activate account",
            description = "Activates a CLIENT, AUDITOR, or MEMBER account so it can log in again. Requires ADMIN role."
    )
    public Mono<ResponseEntity<ResponseMessage>> activateAccount(Authentication authentication, @PathVariable String email) {
        return adminService.activateAccount(email, authentication.getName())
                .map(response -> new ResponseEntity<>(response, response.getStatus()));
    }

    @PatchMapping("/users/{email}/deactivate")
    @Operation(
            summary = "Deactivate account",
            description = "Deactivates a CLIENT, AUDITOR, or MEMBER account, blocking further logins. Requires ADMIN role."
    )
    public Mono<ResponseEntity<ResponseMessage>> deactivateAccount(Authentication authentication, @PathVariable String email) {
        return adminService.deactivateAccount(email, authentication.getName())
                .map(response -> new ResponseEntity<>(response, response.getStatus()));
    }
}
