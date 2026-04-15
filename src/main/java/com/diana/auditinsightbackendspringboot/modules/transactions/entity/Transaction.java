package com.diana.auditinsightbackendspringboot.modules.transactions.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;
    private Double amount;
    private String counterparty;
    private String type;
    private String source;
    private String status;
    private Integer riskScore;
}
