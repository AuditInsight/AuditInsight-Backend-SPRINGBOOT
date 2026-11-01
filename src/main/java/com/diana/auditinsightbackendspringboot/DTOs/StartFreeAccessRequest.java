package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StartFreeAccessRequest(
        @NotNull(message = "Duration in days is required")
        @Min(value = 1, message = "Free access must be at least 1 day")
        @Max(value = 3650, message = "Free access cannot exceed 3650 days")
        Integer durationDays
) {}
