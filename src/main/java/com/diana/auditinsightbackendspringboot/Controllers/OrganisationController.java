package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Services.OrganisationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/organisations")
@Tag(name = "Organisation", description = "Organisation management — create, update, members, invitations")
@SecurityRequirement(name = "bearerAuth")
public class OrganisationController {

    private final OrganisationService organisationService;

    public OrganisationController(OrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @PostMapping
    @Operation(summary = "Create organisation", description = "Creates a new organisation. The authenticated CLIENT becomes the owner.")
    public Mono<ResponseEntity<OrganisationResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateOrganisationRequest request) {
        return organisationService.createOrganisation(authentication.getName(), request)
                .map(resp -> new ResponseEntity<>(resp, HttpStatus.CREATED));
    }

    @GetMapping
    @Operation(summary = "List my organisations", description = "Returns all organisations the authenticated user belongs to.")
    public Flux<Organisation> listMine(Authentication authentication) {
        return organisationService.listMyOrganisations(authentication.getName());
    }

    @GetMapping("/{orgId}")
    @Operation(summary = "Get organisation", description = "Returns organisation details. Accessible by any member of the organisation.")
    public Mono<ResponseEntity<OrganisationResponse>> get(
            Authentication authentication,
            @PathVariable UUID orgId) {
        return organisationService.getOrganisation(orgId, authentication.getName())
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{orgId}")
    @Operation(summary = "Update organisation", description = "Updates organisation settings. Only the CLIENT (owner) can perform this.")
    public Mono<ResponseEntity<OrganisationResponse>> update(
            Authentication authentication,
            @PathVariable UUID orgId,
            @RequestBody UpdateOrganisationRequest request) {
        return organisationService.updateOrganisation(orgId, authentication.getName(), request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{orgId}/members/invite")
    @Operation(summary = "Invite member", description = "Invites a user by email. If the user exists they are added immediately; otherwise an invitation email is sent.")
    public Mono<ResponseEntity<ResponseMessage>> invite(
            Authentication authentication,
            @PathVariable UUID orgId,
            @Valid @RequestBody InviteMemberRequest request) {
        return organisationService.inviteMember(orgId, authentication.getName(), request)
                .map(resp -> new ResponseEntity<>(resp, resp.getStatus()));
    }

    @GetMapping("/{orgId}/members")
    @Operation(summary = "List members", description = "Lists all members of the organisation. Accessible by any authenticated member.")
    public Flux<OrganisationMemberResponse> listMembers(
            Authentication authentication,
            @PathVariable UUID orgId) {
        return organisationService.listMembers(orgId, authentication.getName());
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    @Operation(summary = "Remove member", description = "Removes a member from the organisation. Only the CLIENT can perform this. The owner cannot be removed.")
    public Mono<ResponseEntity<ResponseMessage>> removeMember(
            Authentication authentication,
            @PathVariable UUID orgId,
            @PathVariable Long userId) {
        return organisationService.removeMember(orgId, authentication.getName(), userId)
                .map(resp -> new ResponseEntity<>(resp, resp.getStatus()));
    }

    @PatchMapping("/{orgId}/transfer-ownership")
    @Operation(summary = "Transfer ownership", description = "Transfers the CLIENT role to an existing member identified by email. Only the current CLIENT can do this.")
    public Mono<ResponseEntity<ResponseMessage>> transferOwnership(
            Authentication authentication,
            @PathVariable UUID orgId,
            @RequestParam String newOwnerEmail) {
        return organisationService.transferOwnership(orgId, authentication.getName(), newOwnerEmail)
                .map(resp -> new ResponseEntity<>(resp, resp.getStatus()));
    }

    @GetMapping("/{orgId}/invitations")
    @Operation(summary = "List pending invitations", description = "Returns all PENDING invitations for the organisation. Only the CLIENT can view this.")
    public Flux<OrganisationInvitationResponse> listInvitations(
            Authentication authentication,
            @PathVariable UUID orgId) {
        return organisationService.listPendingInvitations(orgId, authentication.getName());
    }

    @DeleteMapping("/{orgId}/invitations/{invitationId}")
    @Operation(summary = "Revoke invitation", description = "Revokes a PENDING invitation. Only the CLIENT can perform this.")
    public Mono<ResponseEntity<ResponseMessage>> revokeInvitation(
            Authentication authentication,
            @PathVariable UUID orgId,
            @PathVariable UUID invitationId) {
        return organisationService.revokeInvitation(orgId, authentication.getName(), invitationId)
                .map(resp -> new ResponseEntity<>(resp, resp.getStatus()));
    }
}
