package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;


@Getter
@Setter
public class CreateEvidenceRequest {
    private UUID organisationId;
    private String transactionId;
    private String documentName;
    private String folder;
    private String subfolder;
    private String notes;
}