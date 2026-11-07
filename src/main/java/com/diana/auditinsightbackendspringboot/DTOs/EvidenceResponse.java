package com.diana.auditinsightbackendspringboot.DTOs;

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
}