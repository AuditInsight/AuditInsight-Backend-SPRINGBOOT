package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** A one-time code emailed to a user so they can confirm their email right before signing one document. */
@Table("signing_verifications")
@Getter
@Setter
public class SigningVerification {

    @Id
    @Column("id")
    private UUID id;

    @Column("user_id")
    private Long userId;

    @Column("evidence_id")
    private UUID evidenceId;

    // SHA-256 of the code; the code itself is never stored.
    @Column("code_hash")
    private String codeHash;

    @Column("attempts")
    private int attempts;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("consumed_at")
    private LocalDateTime consumedAt;

    @Column("created_at")
    private LocalDateTime createdAt;
}
