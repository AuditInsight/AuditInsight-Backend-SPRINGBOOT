package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.UpdateClientProfileRequest;
import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Services.ClientProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/client")
@Tag(name = "Client Profile", description = "Profile management for authenticated clients")
@SecurityRequirement(name = "bearerAuth")
public class ClientProfileController {

    private final ClientProfileService clientProfileService;

    public ClientProfileController(ClientProfileService clientProfileService) {
        this.clientProfileService = clientProfileService;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Get my profile", description = "Returns the profile of the currently authenticated client")
    public Mono<ResponseEntity<ClientProfile>> getProfile(Authentication authentication) {
        return clientProfileService.getProfile(authentication.getName())
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Complete my profile", description = "Partially updates phone, address, company name, or name for the authenticated client")
    public Mono<ResponseEntity<ClientProfile>> updateProfile(
            Authentication authentication,
            @RequestBody UpdateClientProfileRequest request) {
        return clientProfileService.updateProfile(authentication.getName(), request)
                .map(ResponseEntity::ok);
    }
}
