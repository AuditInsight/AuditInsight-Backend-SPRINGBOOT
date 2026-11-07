package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Starts the platform-wide emergency override: an incident tool, not a trial or promotion. */
public record StartFreeAccessRequest(
        @NotNull(message = "Duration in days is required")
        @Min(value = 1, message = "The emergency override must last at least 1 day")
        @Max(value = 30, message = "The emergency override cannot exceed 30 days")
        Integer durationDays,
        @NotBlank(message = "A reason is required for the emergency override")
        @Size(max = 1000, message = "reason is too long")
        String reason
) {}
