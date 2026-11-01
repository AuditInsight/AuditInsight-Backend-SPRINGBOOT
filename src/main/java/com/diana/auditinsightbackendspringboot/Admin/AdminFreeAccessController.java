package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminFreeAccessResponse;
import com.diana.auditinsightbackendspringboot.DTOs.StartFreeAccessRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/free-access")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFreeAccessController {

    private final AdminFreeAccessService freeAccessService;

    public AdminFreeAccessController(AdminFreeAccessService freeAccessService) {
        this.freeAccessService = freeAccessService;
    }

    @GetMapping
    public Mono<AdminFreeAccessResponse> getStatus() {
        return freeAccessService.getStatus();
    }

    @PostMapping("/start")
    public Mono<AdminFreeAccessResponse> start(
            Authentication authentication,
            @Valid @RequestBody StartFreeAccessRequest request) {
        return freeAccessService.start(request.durationDays(), authentication.getName());
    }

    @PostMapping("/end")
    public Mono<AdminFreeAccessResponse> end(Authentication authentication) {
        return freeAccessService.end(authentication.getName());
    }
}
