package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialSource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A free trial explicitly granted to one organisation by a Super Admin, directly or through a campaign at
 * registration. It gives access while ACTIVE and {@code startsAt <= now < endsAt}.
 */
@Table("trial_grants")
@Getter
@Setter
public class TrialGrant {

    @Id
    @Column("id")
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("owner_client_id")
    private UUID ownerClientId;

    @Column("source")
    private TrialSource source;

    @Column("campaign_id")
    private UUID campaignId;

    @Column("status")
    private TrialGrantStatus status;

    @Column("duration_days")
    private Integer durationDays;

    @Column("starts_at")
    private LocalDateTime startsAt;

    @Column("ends_at")
    private LocalDateTime endsAt;

    @Column("granted_by")
    private Long grantedBy;

    @Column("reason")
    private String reason;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("revoked_at")
    private LocalDateTime revokedAt;

    @Column("revoked_by")
    private Long revokedBy;

    @Column("revoke_reason")
    private String revokeReason;

    @Column("reminder_sent")
    private boolean reminderSent;

    /** Whether this grant gives access at {@code now}. */
    public boolean isCurrent(LocalDateTime now) {
        return status == TrialGrantStatus.ACTIVE && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
