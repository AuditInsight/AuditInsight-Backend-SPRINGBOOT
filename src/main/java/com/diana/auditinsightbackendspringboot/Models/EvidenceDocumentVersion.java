package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** A stored, signed rendition of an evidence document. The original upload is version 0 and lives on Evidence. */
@Table("evidence_document_versions")
@Getter
@Setter
public class EvidenceDocumentVersion {

    @Id
    @Column("id")
    private UUID id;

    @Column("evidence_id")
    private UUID evidenceId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("version_no")
    private Integer versionNo;

    @Column("file_url")
    private String fileUrl;

    @Column("file_name")
    private String fileName;

    @Column("sha256")
    private String sha256;

    @Column("size_bytes")
    private Long sizeBytes;

    @Column("signature_count")
    private Integer signatureCount;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;
}
