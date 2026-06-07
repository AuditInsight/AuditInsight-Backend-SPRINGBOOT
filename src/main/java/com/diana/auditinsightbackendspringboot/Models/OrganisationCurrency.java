package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("organisation_currency")
@Getter
@Setter
public class OrganisationCurrency {

    @Id
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("currency_code")
    private String currencyCode;

    @Column("is_default")
    private boolean primaryCurrency;
}
