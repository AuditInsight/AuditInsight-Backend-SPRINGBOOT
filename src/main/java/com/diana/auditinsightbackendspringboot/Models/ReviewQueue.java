package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.IssueType;
import com.diana.auditinsightbackendspringboot.Enum.ReviewStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("review_queue")
@Getter
@Setter
public class ReviewQueue {

    @Id
    @Column("id")
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("transaction_id")
    private String transactionId;

    @Column("issue_type")
    private IssueType issueType;

    @Column("description")
    private String description;

    @Column("status")
    private ReviewStatus status = ReviewStatus.OPEN;

    @Column("flagged_by")
    private String flaggedBy;

    @Column("resolved_by")
    private Long resolvedBy;

    @Column("resolution_note")
    private String resolutionNote;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;
}