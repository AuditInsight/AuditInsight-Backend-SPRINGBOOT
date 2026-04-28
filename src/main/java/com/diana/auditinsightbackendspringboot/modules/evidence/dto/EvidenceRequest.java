package com.diana.auditinsightbackendspringboot.modules.evidence.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EvidenceRequest {

    private String name;

    private String category;

    private String subCategory;

    private String type;

    private String url;

    private LocalDate date;

    private String uploadedBy;

    private String status;

    private String notes;

    private Long transactionId;
}