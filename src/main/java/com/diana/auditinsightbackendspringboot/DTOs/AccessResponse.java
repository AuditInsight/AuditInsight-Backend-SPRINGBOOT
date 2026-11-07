package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.AccessBasis;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionSource;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Enum.TrialSource;

import java.time.LocalDateTime;

/**
 * Whether an organisation can use its workspace right now and why — the only thing the app should use to decide
 * whether to show the workspace or the paywall.
 */
public record AccessResponse(
        boolean hasAccess,
        AccessBasis basis,
        LocalDateTime accessUntil,
        PaidPeriod currentPaid,
        PaidPeriod nextPaid,
        Trial trial,
        LocalDateTime emergencyUntil,
        SubscriptionType requestedPlan) {

    public record PaidPeriod(SubscriptionType subscriptionType, LocalDateTime startDate, LocalDateTime endDate,
                             SubscriptionSource source) {}

    /** {@code current} is false once the trial has ended (or was revoked). */
    public record Trial(TrialSource source, int durationDays, LocalDateTime startsAt, LocalDateTime endsAt,
                        boolean current) {}
}
