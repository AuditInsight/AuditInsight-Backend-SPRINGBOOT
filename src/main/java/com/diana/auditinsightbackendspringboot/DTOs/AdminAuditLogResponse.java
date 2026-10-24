package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminAuditLogResponse {
    private UUID id;
    private String adminUsername;
    private String action;
    private String targetType;
    private String targetId;
    private String beforeState;
    private String afterState;
    private LocalDateTime createdAt;
}
