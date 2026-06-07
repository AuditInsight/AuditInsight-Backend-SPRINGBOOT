package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrganisationResponse {
    private String message;
    private UUID organisationId;
    private String name;
    private String industry;
    private String fiscalYearStart;
    private String fiscalYearEnd;
    private String defaultCurrency;
    private List<String> currencies;
    private LocalDateTime createdAt;
}
