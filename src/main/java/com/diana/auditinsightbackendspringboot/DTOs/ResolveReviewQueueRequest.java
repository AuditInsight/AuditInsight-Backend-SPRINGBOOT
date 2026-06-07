package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveReviewQueueRequest {

    @NotBlank(message = "Resolution note is required")
    private String resolutionNote;
}