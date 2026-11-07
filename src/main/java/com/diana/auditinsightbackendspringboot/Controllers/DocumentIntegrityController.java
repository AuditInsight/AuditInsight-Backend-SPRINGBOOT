package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.AuditEventResponse;
import com.diana.auditinsightbackendspringboot.DTOs.DocumentVersionsResponse;
import com.diana.auditinsightbackendspringboot.DTOs.RequestMeta;
import com.diana.auditinsightbackendspringboot.DTOs.VerificationResponse;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Services.AuditTrailService;
import com.diana.auditinsightbackendspringboot.Services.DocumentIntegrityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/evidence/{evidenceId}")
@Tag(name = "Document integrity", description = "Original files, signed version history, copy verification and audit trail")
@SecurityRequirement(name = "bearerAuth")
public class DocumentIntegrityController {

    static final String HEADER_SHA256 = "X-Document-SHA256";
    /** Largest file accepted for verification; matches the largest file the app downloads from storage. */
    static final int MAX_VERIFY_BYTES = 30 * 1024 * 1024;

    private final DocumentIntegrityService integrityService;
    private final AuditTrailService auditTrailService;

    public DocumentIntegrityController(DocumentIntegrityService integrityService, AuditTrailService auditTrailService) {
        this.integrityService = integrityService;
        this.auditTrailService = auditTrailService;
    }

    @GetMapping("/original")
    @Operation(summary = "Get the original file",
            description = "Serves the original upload to organisation members after checking it still matches the " +
                    "SHA-256 recorded at upload, and records the access in the audit trail. Use this instead of the " +
                    "storage link so files don't have to be publicly reachable. download=true asks the browser to save it.")
    public Mono<ResponseEntity<byte[]>> original(
            Authentication auth,
            @PathVariable UUID evidenceId,
            @RequestParam(defaultValue = "false") boolean download,
            ServerHttpRequest request) {
        return integrityService.original(auth.getName(), evidenceId, RequestMeta.from(request))
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, (download
                                ? ContentDisposition.attachment() : ContentDisposition.inline())
                                .filename(file.fileName()).build().toString())
                        .header(HEADER_SHA256, file.sha256())
                        .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                String.join(", ", HttpHeaders.CONTENT_DISPOSITION, HEADER_SHA256))
                        .body(file.bytes()));
    }

    @GetMapping("/versions")
    @Operation(summary = "Version history",
            description = "The original (version 0) and every stored signed version with its SHA-256 fingerprint, " +
                    "size, signature count and creation time.")
    public Mono<ResponseEntity<DocumentVersionsResponse>> versions(
            Authentication auth,
            @PathVariable UUID evidenceId) {
        return integrityService.versions(auth.getName(), evidenceId).map(ResponseEntity::ok);
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Verify a copy",
            description = "Checks whether an uploaded file is byte-for-byte the original, one of the stored signed " +
                    "versions (and which signatures it holds), or neither — i.e. it was changed. The file is only " +
                    "hashed, never stored. The check is recorded in the audit trail.")
    public Mono<ResponseEntity<VerificationResponse>> verify(
            Authentication auth,
            @PathVariable UUID evidenceId,
            @RequestPart("file") FilePart file,
            ServerHttpRequest request) {
        return DataBufferUtils.join(file.content(), MAX_VERIFY_BYTES)
                .onErrorMap(e -> !(e instanceof InvalidRecord),
                        e -> new InvalidRecord("The file is too large to verify (max 30 MB)"))
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                })
                .flatMap(bytes -> integrityService.verify(auth.getName(), evidenceId, bytes, RequestMeta.from(request)))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/audit-trail")
    @Operation(summary = "Audit trail",
            description = "Everything that happened to this document, oldest first: upload, signature requests, " +
                    "declines, signatures, stored versions, file access and verification checks — with who, when " +
                    "(UTC), IP address, browser and the SHA-256 of the file involved.")
    public Flux<AuditEventResponse> auditTrail(
            Authentication auth,
            @PathVariable UUID evidenceId) {
        return auditTrailService.list(auth.getName(), evidenceId);
    }
}
