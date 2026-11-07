package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Super Admin-run free-trial offer for NEW organisations. While enabled and within its window, an eligible
 * organisation registering receives a trial grant of {@code durationDays}. It never grants access by itself.
 */
@Table("trial_campaigns")
@Getter
@Setter
public class TrialCampaign {

    @Id
    @Column("id")
    private UUID id;

    @Column("name")
    private String name;

    @Column("duration_days")
    private Integer durationDays;

    @Column("starts_at")
    private LocalDateTime startsAt;

    @Column("ends_at")
    private LocalDateTime endsAt;

    @Column("enabled")
    private boolean enabled;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    /** Whether new organisations registering at {@code now} can receive this campaign's trial. */
    public boolean isGranting(LocalDateTime now) {
        return enabled && !now.isBefore(startsAt) && (endsAt == null || now.isBefore(endsAt));
    }
}
