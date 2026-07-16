package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanResponse {
    private PlanTier tier;
    private String description;
    private BigDecimal monthlyPriceUsd;
    private BigDecimal yearlyPriceUsd;
    private Integer maxUsers;
    private Integer auditsPerMonth;
    private Integer storageGb;
    private boolean basicReports;
    private boolean emailSupport;
    private boolean advancedReports;
    private boolean auditTrail;
    private boolean prioritySupport;
    private boolean customWorkflows;
    private boolean complianceTemplates;
    private boolean apiAccess;
    private boolean support247;
    private boolean ssoSaml;
    private boolean dedicatedAccountManager;
    private boolean customIntegrations;
    private boolean slaGuarantee;
}
