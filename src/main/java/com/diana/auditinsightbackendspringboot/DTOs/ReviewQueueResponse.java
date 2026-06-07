package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.IssueType;
import com.diana.auditinsightbackendspringboot.Enum.ReviewStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ReviewQueueResponse {
    private UUID id;
    private UUID organisationId;
    private String transactionId;
    private IssueType issueType;
    private String description;
    private ReviewStatus status;
    private String flaggedBy;
    private Long resolvedBy;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}