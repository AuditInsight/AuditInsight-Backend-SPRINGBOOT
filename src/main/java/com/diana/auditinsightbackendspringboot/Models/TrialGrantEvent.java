package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.TrialEventType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** One entry in a trial grant's append-only history. */
@Table("trial_grant_events")
@Getter
@Setter
public class TrialGrantEvent {

    @Id
    @Column("id")
    private UUID id;

    @Column("grant_id")
    private UUID grantId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("event_type")
    private TrialEventType eventType;

    @Column("old_ends_at")
    private LocalDateTime oldEndsAt;

    @Column("new_ends_at")
    private LocalDateTime newEndsAt;

    @Column("admin_user_id")
    private Long adminUserId;

    @Column("reason")
    private String reason;

    @Column("details")
    private String details;

    @Column("created_at")
    private LocalDateTime createdAt;
}
