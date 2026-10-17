package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.SignatureRequestStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SignatureRequestResponse {
    private UUID id;
    private UUID evidenceId;
    private String documentName;
    private UUID organisationId;
    private Long requestedBy;
    private String requestedByName;
    private Long requestedUserId;
    private String requestedUserName;
    private String message;
    private SignatureRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
