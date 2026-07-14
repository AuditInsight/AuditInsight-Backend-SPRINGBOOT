# Dynamic Evidence Folder Structure by Organisation Type

## Overview

AuditInsight is managing the transaction that are being made on a specific organisation . now since we support different organisation (private and the NGO) then the transaction demanding are different 

---

# Objective

Provide and handle the transaction  structure that:

- fit to the type of organisation 
- have on entity representing transaction in database 
- Makes sure that each organisation have its fit transaction request

---

# Supported Organisation Types

Currently, the platform supports:

- Private 
- NGO 

---

# Transaction  Structures

The system maintains separate transaction request for each organisation type.

## 1. Private

The current implemented transaction request in src/main/java/com/diana/auditinsightbackendspringboot/DTOs/CreateTransactionRequest.java matches exactly the PRIVATE organisation type

---

## 2. NGO

this is what to be requestes for the NGO organisation type
- Date
- Project
- Donor
- Budget Line
- Amount
- Counterparty
- Status
- Evidence


---

# Current table 

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


---

# changes needed 

the system since support the PRIVATE and the NGO. I want to include the donor and the budgetline for the transanction and also to be included
in the createTransactionRequest dto. then in the seervice that where the validation wil happen if private stick to the current implementd logic , 
then if NGO they must provide the donor and the bugdet line 

no more dto for creating transaction request or other transaction relations , when field not provided then it is going to be null in the relations
