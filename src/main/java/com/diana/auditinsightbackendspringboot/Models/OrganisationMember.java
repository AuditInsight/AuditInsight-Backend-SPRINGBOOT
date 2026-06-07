package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("organisation_member")
@Getter
@Setter
public class OrganisationMember {

    @Id
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("user_id")
    private Long userId;

    private Role role;

    private MemberStatus status;

    @Column("joined_at")
    private LocalDateTime joinedAt;
}
