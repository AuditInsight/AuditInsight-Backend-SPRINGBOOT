package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.UpdateAuditorProfileRequest;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Services.AuditorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auditor")
@Tag(name = "Auditor Profile", description = "Profile management for authenticated auditors")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('AUDITOR')")
public class AuditorProfileController {

    private final AuditorProfileService auditorProfileService;

    public AuditorProfileController(AuditorProfileService auditorProfileService) {
        this.auditorProfileService = auditorProfileService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get my profile", description = "Returns the profile of the currently authenticated auditor")
    public Mono<ResponseEntity<AuditorProfile>> getProfile(Authentication authentication) {
        return auditorProfileService.getProfile(authentication.getName())
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/profile")
    @Operation(summary = "Complete my profile", description = "Partially updates phone or certification number for the authenticated auditor")
    public Mono<ResponseEntity<AuditorProfile>> updateProfile(
            Authentication authentication,
            @RequestBody UpdateAuditorProfileRequest request) {
        return auditorProfileService.updateProfile(authentication.getName(), request)
                .map(ResponseEntity::ok);
    }
}
