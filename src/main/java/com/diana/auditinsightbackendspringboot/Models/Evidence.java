package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("evidence")
@Getter
@Setter
public class Evidence {

    @Id
    @Column("id")
    private UUID id;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("transaction_id")
    private String transactionId;

    @Column("document_name")
    private String documentName;

    @Column("folder")
    private String folder;

    @Column("subfolder")
    private String subfolder;

    @Column("file_upload")
    private String fileUpload;

    @Column("file_type")
    private String fileType;

    @Column("notes")
    private String notes;

    @Column("uploaded_by")
    private Long uploadedBy;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;
}
