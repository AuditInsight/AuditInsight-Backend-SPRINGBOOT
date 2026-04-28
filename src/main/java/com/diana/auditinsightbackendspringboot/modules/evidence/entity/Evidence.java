package com.diana.auditinsightbackendspringboot.modules.evidence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "evidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String category;

    private String subCategory;

    private String type; // Document, Image, etc.

    private String url;

    private LocalDate date;

    private String uploadedBy;

    private LocalDateTime uploadedAt;

    private String status; // Pending, Verified, Missing

    @Column(length = 2000)
    private String notes;

    // 🔗 Optional link to transaction (matches your frontend)
    private Long transactionId;
}