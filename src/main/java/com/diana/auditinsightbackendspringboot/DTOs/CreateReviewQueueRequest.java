package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateReviewQueueRequest {

    @NotNull(message = "Organisation ID is required")
    private UUID organisationId;

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotNull(message = "Issue type is required")
    private IssueType issueType;

    @NotBlank(message = "Description is required")
    private String description;
}