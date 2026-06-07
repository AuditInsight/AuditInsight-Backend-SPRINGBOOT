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
    private String transactionId;
    private String documentName;
    private String folder;
    private String subfolder;
    private String fileUpload;
    private String fileType;
    private String notes;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}