-- Document integrity and audit trail (e-signing Phase 3).

-- SHA-256 of the original upload (version 0). Written at upload; for older documents it is recorded the first
-- time the original is fetched through the API.
ALTER TABLE evidence ADD COLUMN original_sha256 CHAR(64);

-- Where a signature came from, kept with the signature as evidence of who signed.
ALTER TABLE evidence_signatures
    ADD COLUMN signer_ip VARCHAR(64),
    ADD COLUMN signer_user_agent VARCHAR(512);

-- Append-only activity log for a document: who did what, when, from where, and on which exact file.
CREATE TABLE evidence_audit_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id     UUID         NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    event_type      VARCHAR(40)  NOT NULL CHECK (event_type IN (
                        'DOCUMENT_UPLOADED', 'SIGNATURE_REQUESTED', 'SIGNATURE_DECLINED', 'SIGNED',
                        'VERSION_STORED', 'SIGNED_COPY_ACCESSED', 'ORIGINAL_ACCESSED', 'VERIFICATION')),
    actor_id        BIGINT       REFERENCES users(id),
    actor_name      VARCHAR(255),
    version_no      INT,
    sha256          CHAR(64),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    details         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_evidence_audit_events_evidence ON evidence_audit_events (evidence_id, created_at);
