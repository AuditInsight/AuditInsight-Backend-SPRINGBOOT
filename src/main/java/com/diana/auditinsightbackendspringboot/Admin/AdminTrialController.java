package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.TrialRequests;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Services.TrialCampaignService;
import com.diana.auditinsightbackendspringboot.Services.TrialGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Super Admin control of free trials. Organisations can never grant, extend or shorten their own trial: every
 * endpoint here requires the platform ADMIN role, and every change is recorded with the admin and a reason.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Trials", description = "Super Admin-authorised free trials and trial campaigns")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrialController {

    private final TrialGrantService trialGrantService;
    private final TrialCampaignService trialCampaignService;

    public AdminTrialController(TrialGrantService trialGrantService, TrialCampaignService trialCampaignService) {
        this.trialGrantService = trialGrantService;
        this.trialCampaignService = trialCampaignService;
    }

    // ── Individual trials ─────────────────────────────────────────────────────────

    @PostMapping("/organisations/{organisationId}/trials")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Grant a free trial",
            description = "Starts a trial of the given number of days now. Fails if the organisation already has an "
                    + "active trial (extend or shorten that one instead).")
    public Mono<TrialGrant> grant(Authentication auth, @PathVariable UUID organisationId,
                                  @Valid @RequestBody TrialRequests.GrantTrial body) {
        return trialGrantService.grant(organisationId, body.days(), body.reason(), auth.getName());
    }

    @PatchMapping("/organisations/{organisationId}/trials/current")
    @Operation(summary = "Extend or shorten the current trial",
            description = "Moves the end of the organisation's active trial. Paid time bought during the trial moves "
                    + "with it, so it still starts when the trial ends. A reason is required.")
    public Mono<TrialGrant> changeEnd(Authentication auth, @PathVariable UUID organisationId,
                                      @Valid @RequestBody TrialRequests.ChangeTrialEnd body) {
        return trialGrantService.changeEnd(organisationId, body.newEndsAt(), body.reason(), auth.getName());
    }

    @PostMapping("/organisations/{organisationId}/trials/current/revoke")
    @Operation(summary = "Revoke the current trial",
            description = "Ends the active trial now. Paid time bought during the trial starts now instead of at the "
                    + "old trial end. A reason is required.")
    public Mono<TrialGrant> revoke(Authentication auth, @PathVariable UUID organisationId,
                                   @Valid @RequestBody TrialRequests.Reason body) {
        return trialGrantService.revoke(organisationId, body.reason(), auth.getName());
    }

    @GetMapping("/organisations/{organisationId}/trials")
    @Operation(summary = "Trial history", description = "Every trial the organisation received and every change to it.")
    public Mono<TrialGrantService.TrialHistory> history(@PathVariable UUID organisationId) {
        return trialGrantService.history(organisationId);
    }

    // ── Campaigns ─────────────────────────────────────────────────────────────────

    @GetMapping("/trial-campaigns")
    @Operation(summary = "List trial campaigns")
    public Flux<TrialCampaignService.CampaignView> campaigns() {
        return trialCampaignService.list();
    }

    @PostMapping("/trial-campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a trial campaign",
            description = "Created disabled. While enabled and within its window, eligible NEW organisations receive a "
                    + "trial of this length at registration (one campaign trial per owner, ever).")
    public Mono<TrialCampaignService.CampaignView> createCampaign(Authentication auth,
                                                                  @Valid @RequestBody TrialRequests.CreateCampaign body) {
        return trialCampaignService.create(body.name(), body.durationDays(), body.startsAt(), body.endsAt(),
                body.reason(), auth.getName());
    }

    @PostMapping("/trial-campaigns/{campaignId}/enable")
    @Operation(summary = "Enable a trial campaign", description = "Only one campaign can be enabled at a time.")
    public Mono<TrialCampaignService.CampaignView> enable(Authentication auth, @PathVariable UUID campaignId,
                                                          @Valid @RequestBody TrialRequests.Reason body) {
        return trialCampaignService.enable(campaignId, body.reason(), auth.getName());
    }

    @PostMapping("/trial-campaigns/{campaignId}/disable")
    @Operation(summary = "Disable a trial campaign",
            description = "Stops new grants. Trials already granted keep running until their own end date.")
    public Mono<TrialCampaignService.CampaignView> disable(Authentication auth, @PathVariable UUID campaignId,
                                                           @Valid @RequestBody TrialRequests.Reason body) {
        return trialCampaignService.disable(campaignId, body.reason(), auth.getName());
    }
}
