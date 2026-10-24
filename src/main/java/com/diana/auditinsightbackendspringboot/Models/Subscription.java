package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("subscriptions")
@Getter
@Setter
public class Subscription {

    @Id
    @Column("id")
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    /** Null while {@link #status} is {@code TRIAL} — the trial isn't tied to a paid period. */
    @Column("subscription_type")
    private SubscriptionType subscriptionType;

    @Column("status")
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column("start_date")
    private LocalDateTime startDate;

    @Column("end_date")
    private LocalDateTime endDate;

    @Column("trial_reminder_sent")
    private boolean trialReminderSent;

    @Column("expiry_reminder_sent")
    private boolean expiryReminderSent;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
