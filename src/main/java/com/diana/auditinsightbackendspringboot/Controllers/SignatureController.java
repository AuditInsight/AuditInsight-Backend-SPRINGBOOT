package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.EvidenceSignaturesResponse;
import com.diana.auditinsightbackendspringboot.DTOs.RequestSignatureRequest;
import com.diana.auditinsightbackendspringboot.DTOs.SignEvidenceRequest;
import com.diana.auditinsightbackendspringboot.DTOs.SignatureRequestResponse;
import com.diana.auditinsightbackendspringboot.DTOs.SignatureResponse;
import com.diana.auditinsightbackendspringboot.Services.SignatureService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Signatures", description = "Optional e-signatures on evidence documents")
@SecurityRequirement(name = "bearerAuth")
public class SignatureController {

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping("/evidence/{evidenceId}/signatures")
    @Operation(summary = "Sign a document",
            description = "Signs the evidence document as the logged-in user. Optional: nobody has to ask you first. " +
                    "The signer's name and the time are recorded by the server. Any active member of the " +
                    "organisation (including auditors) can sign; each person can sign a document once. " +
                    "If someone had requested your signature, that request is marked SIGNED.")
    public Mono<ResponseEntity<SignatureResponse>> sign(
            Authentication auth,
            @PathVariable UUID evidenceId,
            @Valid @RequestBody(required = false) SignEvidenceRequest body) {
        return signatureService.sign(auth.getName(), evidenceId, body == null ? new SignEvidenceRequest() : body)
                .map(r -> new ResponseEntity<>(r, HttpStatus.CREATED));
    }

    @GetMapping("/evidence/{evidenceId}/signatures")
    @Operation(summary = "List signatures on a document",
            description = "Returns who signed (name and time) plus any signature requests still pending. All members.")
    public Mono<ResponseEntity<EvidenceSignaturesResponse>> list(
            Authentication auth,
            @PathVariable UUID evidenceId) {
        return signatureService.list(auth.getName(), evidenceId).map(ResponseEntity::ok);
    }

    @PostMapping("/evidence/{evidenceId}/signature-requests")
    @Operation(summary = "Ask members to sign a document",
            description = "Creates a pending signature request for each selected active member of the organisation. " +
                    "Requests are optional nudges; those members (and anyone else) can still sign without one.")
    public Mono<ResponseEntity<List<SignatureRequestResponse>>> request(
            Authentication auth,
            @PathVariable UUID evidenceId,
            @Valid @RequestBody RequestSignatureRequest body) {
        return signatureService.requestSignatures(auth.getName(), evidenceId, body)
                .map(r -> new ResponseEntity<>(r, HttpStatus.CREATED));
    }

    @GetMapping("/signature-requests/pending")
    @Operation(summary = "My pending signature requests",
            description = "Signature requests addressed to the logged-in user that are still waiting.")
    public Flux<SignatureRequestResponse> pending(Authentication auth) {
        return signatureService.pendingForMe(auth.getName());
    }

    @PostMapping("/signature-requests/{requestId}/decline")
    @Operation(summary = "Decline a signature request",
            description = "Declines a request addressed to you. You can still sign the document later if you change your mind.")
    public Mono<ResponseEntity<SignatureRequestResponse>> decline(
            Authentication auth,
            @PathVariable UUID requestId) {
        return signatureService.decline(auth.getName(), requestId).map(ResponseEntity::ok);
    }
}
