package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("admin_audit_log")
@Getter
@Setter
public class AdminAuditLog {

    @Id
    private UUID id;

    @Column("admin_user_id")
    private Long adminUserId;

    private String action;

    @Column("target_type")
    private String targetType;

    @Column("target_id")
    private String targetId;

    @Column("before_state")
    private String beforeState;

    @Column("after_state")
    private String afterState;

    @Column("created_at")
    private LocalDateTime createdAt;
}
