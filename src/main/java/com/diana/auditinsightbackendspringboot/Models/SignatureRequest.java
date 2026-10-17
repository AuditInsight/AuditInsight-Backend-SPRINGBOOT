package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.SignatureRequestStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("signature_requests")
@Getter
@Setter
public class SignatureRequest {

    @Id
    @Column("id")
    private UUID id;

    @Column("evidence_id")
    private UUID evidenceId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("requested_by")
    private Long requestedBy;

    @Column("requested_by_name")
    private String requestedByName;

    @Column("requested_user_id")
    private Long requestedUserId;

    @Column("requested_user_name")
    private String requestedUserName;

    @Column("message")
    private String message;

    @Column("status")
    private SignatureRequestStatus status = SignatureRequestStatus.PENDING;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("responded_at")
    private LocalDateTime respondedAt;
}
