package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("platform_access_settings")
@Getter
@Setter
public class PlatformAccessSettings {

    public static final String GLOBAL_ID = "global";

    @Id
    @Column("setting_key")
    private String id;

    @Column("free_access_started_at")
    private LocalDateTime freeAccessStartedAt;

    @Column("free_access_ends_at")
    private LocalDateTime freeAccessEndsAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("updated_by")
    private Long updatedBy;
}
