package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EvidenceResponse {
    private UUID id;
    private UUID organisationId;
    private UUID transactionId;
    private String documentName;
    private String folder;
    private String subfolder;
    // The API path that serves the original (access-checked, integrity-checked, audited) — never the storage URL,
    // so files don't need to be publicly reachable. Null when the evidence has no stored file.
    private String fileUpload;
    private String fileType;
    private String notes;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    // How many people have signed this document; 0 means Preview/Download should use the original file.
    private int signatureCount;
    // True when the document is signed on the page (PDF, image, or Word/Excel via PDF conversion); false when
    // signing produces a separate signature certificate.
    private boolean placeable;

    /** {@code /api/evidence/{id}/original} when the evidence has a stored file, else null. */
    public static String originalPath(Evidence e) {
        return e.getFileUpload() == null || e.getFileUpload().isBlank() ? null : "/api/evidence/" + e.getId() + "/original";
    }
}