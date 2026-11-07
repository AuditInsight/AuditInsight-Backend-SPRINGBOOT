package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One entry in a document's append-only activity log. */
@Table("evidence_audit_events")
@Getter
@Setter
public class EvidenceAuditEvent {

    @Id
    @Column("id")
    private UUID id;

    @Column("evidence_id")
    private UUID evidenceId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("event_type")
    private String eventType;

    @Column("actor_id")
    private Long actorId;

    @Column("actor_name")
    private String actorName;

    @Column("version_no")
    private Integer versionNo;

    @Column("sha256")
    private String sha256;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("details")
    private String details;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
