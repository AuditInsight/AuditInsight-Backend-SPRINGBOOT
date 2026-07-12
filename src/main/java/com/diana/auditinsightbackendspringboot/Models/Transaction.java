package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.EvidenceStatus;
import com.diana.auditinsightbackendspringboot.Enum.PaymentMethod;
import com.diana.auditinsightbackendspringboot.Enum.TransactionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TransactionType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("transactions")
@Getter
@Setter
public class Transaction implements Persistable<String> {

    @Id
    @Column("id")
    private String id;

    /** Tells Spring Data R2DBC to INSERT (not UPDATE) when we assign our own ID. */
    @Transient
    private boolean newRecord;

    @Override
    public boolean isNew() {
        return newRecord;
    }

    @Column("organisation_id")
    private UUID organisationId;

    @Column("name")
    private String name;

    @Column("date")
    private LocalDate date;

    @Column("counterparty")
    private String counterparty;

    @Column("donor")
    private String donor;

    @Column("budget_line")
    private String budgetLine;

    @Column("amount")
    private BigDecimal amount;

    @Column("type")
    private TransactionType type;

    @Column("payment_method")
    private PaymentMethod paymentMethod;

    @Column("status")
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column("evidence_status")
    private EvidenceStatus evidenceStatus = EvidenceStatus.MISSING;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;
}
