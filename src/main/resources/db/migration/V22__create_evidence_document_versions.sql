-- Stored signed versions of evidence documents (e-signing Phase 1).
-- The original upload stays in evidence.file_upload (version 0). Every signature produces a new
-- stored PDF here, so Preview and Download serve the exact same bytes, identified by its SHA-256.
-- signature_count records how many signatures the file contains; a version with fewer signatures
-- than the document currently has is stale and gets superseded by a new one.

CREATE TABLE evidence_document_versions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id     UUID         NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    version_no      INT          NOT NULL CHECK (version_no > 0),
    file_url        TEXT         NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    signature_count INT          NOT NULL CHECK (signature_count > 0),
    created_by      BIGINT       REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_evidence_document_version UNIQUE (evidence_id, version_no)
);

CREATE INDEX idx_evidence_document_versions_evidence ON evidence_document_versions (evidence_id, version_no DESC);

-- The stored version a signature first appeared in (NULL for signatures made before this migration).
ALTER TABLE evidence_signatures
    ADD COLUMN document_version_id UUID REFERENCES evidence_document_versions(id) ON DELETE SET NULL;
