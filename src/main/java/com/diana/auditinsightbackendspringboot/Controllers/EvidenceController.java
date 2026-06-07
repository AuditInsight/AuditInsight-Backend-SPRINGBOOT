package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.EvidenceResponse;
import com.diana.auditinsightbackendspringboot.Services.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/evidence")
@Tag(name = "Evidence", description = "Evidence management — upload (multipart), list, view")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload evidence",
            description = "Uploads a file (pdf, xls, xlsx, jpeg, jpg, png) and links it to a transaction. " +
                    "File type is auto-detected from the filename. The file is stored in Cloudinary. " +
                    "CLIENT and MEMBER only."
    )
    public Mono<ResponseEntity<EvidenceResponse>> upload(
            Authentication auth,
            @RequestPart("file") FilePart filePart,
            @RequestPart("organisationId") String organisationId,
            @RequestPart("transactionId") String transactionId,
            @RequestPart("documentName") String documentName,
            @RequestPart("folder") String folder,
            @RequestPart("subfolder") String subfolder,
            @RequestPart(value = "notes", required = false) String notes) {
        return evidenceService.uploadEvidence(
                        auth.getName(),
                        filePart,
                        UUID.fromString(organisationId),
                        transactionId,
                        documentName,
                        folder,
                        subfolder,
                        notes)
                .map(r -> new ResponseEntity<>(r, HttpStatus.CREATED));
    }

    @GetMapping
    @Operation(summary = "List evidence",
               description = "Lists all evidence for the given organisation. All members.")
    public Flux<EvidenceResponse> listByOrg(
            Authentication auth,
            @RequestParam UUID organisationId) {
        return evidenceService.listByOrg(organisationId, auth.getName());
    }

    @GetMapping("/{evidenceId}")
    @Operation(summary = "Get evidence",
               description = "Returns a single evidence record. All members.")
    public Mono<ResponseEntity<EvidenceResponse>> getEvidence(
            Authentication auth,
            @PathVariable UUID evidenceId) {
        return evidenceService.getEvidence(evidenceId, auth.getName())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "List evidence by transaction",
               description = "Returns all evidence linked to a specific transaction. All members.")
    public Flux<EvidenceResponse> listByTransaction(
            Authentication auth,
            @PathVariable String transactionId) {
        return evidenceService.listByTransaction(transactionId, auth.getName());
    }
}