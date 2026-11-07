package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Super Admin request bodies for trials and campaigns. Dates and durations are validated again in the services. */
public final class TrialRequests {

    private TrialRequests() {}

    public record GrantTrial(
            @NotNull(message = "days is required") @Min(value = 1, message = "A trial must be at least 1 day")
            @Max(value = 365, message = "A trial cannot exceed 365 days") Integer days,
            @Size(max = 1000, message = "reason is too long") String reason) {}

    public record ChangeTrialEnd(
            @NotNull(message = "newEndsAt is required") LocalDateTime newEndsAt,
            @NotBlank(message = "A reason is required") @Size(max = 1000, message = "reason is too long") String reason) {}

    public record Reason(
            @NotBlank(message = "A reason is required") @Size(max = 1000, message = "reason is too long") String reason) {}

    public record CreateCampaign(
            @NotBlank(message = "A campaign name is required") @Size(max = 120, message = "name is too long") String name,
            @NotNull(message = "durationDays is required") @Min(value = 1, message = "At least 1 day")
            @Max(value = 365, message = "At most 365 days") Integer durationDays,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            @Size(max = 1000, message = "reason is too long") String reason) {}
}
