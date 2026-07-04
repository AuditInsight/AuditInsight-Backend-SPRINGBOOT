package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("organisation")
@Getter
@Setter
public class Organisation {

    @Id
    private UUID id;

    @Column("client_id")
    private UUID clientId;

    private String name;

    private OrganisationType industry;

    private String size;


    @Column("fiscal_year_start")
    private String fiscalYearStart;

    @Column("fiscal_year_end")
    private String fiscalYearEnd;

    @Column("default_currency")
    private String defaultCurrency;

    @Column("created_at")
    private LocalDateTime createdAt;
}
