package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.InvitationStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("organisation_invitation")
@Getter
@Setter
public class OrganisationInvitation {

    @Id
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("invited_email")
    private String invitedEmail;

    private Role role;

    private String token;

    private InvitationStatus status;

    @Column("invited_by")
    private UUID invitedBy;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("created_at")
    private LocalDateTime createdAt;
}
