package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.BillingCycle;
import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("plans")
@Getter
@Setter
public class Plan {

    @Id
    @Column("tier")
    private PlanTier tier;

    private String description;

    @Column("monthly_price_usd")
    private BigDecimal monthlyPriceUsd;

    @Column("yearly_price_usd")
    private BigDecimal yearlyPriceUsd;

    @Column("max_users")
    private Integer maxUsers;

    @Column("audits_per_month")
    private Integer auditsPerMonth;

    @Column("storage_gb")
    private Integer storageGb;

    @Column("basic_reports")
    private boolean basicReports;

    @Column("email_support")
    private boolean emailSupport;

    @Column("advanced_reports")
    private boolean advancedReports;

    @Column("audit_trail")
    private boolean auditTrail;

    @Column("priority_support")
    private boolean prioritySupport;

    @Column("custom_workflows")
    private boolean customWorkflows;

    @Column("compliance_templates")
    private boolean complianceTemplates;

    @Column("api_access")
    private boolean apiAccess;

    @Column("support_24_7")
    private boolean support247;

    @Column("sso_saml")
    private boolean ssoSaml;

    @Column("dedicated_account_manager")
    private boolean dedicatedAccountManager;

    @Column("custom_integrations")
    private boolean customIntegrations;

    @Column("sla_guarantee")
    private boolean slaGuarantee;

    public BigDecimal priceFor(BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? yearlyPriceUsd : monthlyPriceUsd;
    }
}
