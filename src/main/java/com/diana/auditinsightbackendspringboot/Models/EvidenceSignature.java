package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("evidence_signatures")
@Getter
@Setter
public class EvidenceSignature {

    @Id
    @Column("id")
    private UUID id;

    @Column("evidence_id")
    private UUID evidenceId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("signer_id")
    private Long signerId;

    @Column("signer_name")
    private String signerName;

    @Column("signer_title")
    private String signerTitle;

    @Column("typed_name")
    private String typedName;

    @Column("signature_image")
    private String signatureImage;

    @Column("signed_at")
    private LocalDateTime signedAt;

    @Column("request_id")
    private UUID requestId;

    // The stored signed version this signature first appeared in; null for signatures made before versioning.
    @Column("document_version_id")
    private UUID documentVersionId;

    // SHA-256 of the document the signer placed the signature on; null when the signature wasn't placed.
    @Column("base_sha256")
    private String baseSha256;

    // Where the signature came from (client IP behind the proxy, and browser), as evidence of who signed.
    @Column("signer_ip")
    private String signerIp;

    @Column("signer_user_agent")
    private String signerUserAgent;
}
