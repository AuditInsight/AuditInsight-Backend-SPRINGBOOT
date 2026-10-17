-- Optional e-signatures on evidence documents.
-- Any active member of the organisation may sign any of its evidence voluntarily; a signature
-- request is only a nudge to a specific member and is not required before signing.

CREATE TABLE evidence_signatures (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id     UUID         NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    signer_id       BIGINT       NOT NULL REFERENCES users(id),
    signer_name     VARCHAR(255) NOT NULL,
    typed_name      VARCHAR(255),
    signature_image TEXT,
    signed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id      UUID,
    CONSTRAINT uq_evidence_signer UNIQUE (evidence_id, signer_id)
);

CREATE TABLE signature_requests (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id         UUID         NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    organisation_id     UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    requested_by        BIGINT       NOT NULL REFERENCES users(id),
    requested_by_name   VARCHAR(255) NOT NULL,
    requested_user_id   BIGINT       NOT NULL REFERENCES users(id),
    requested_user_name VARCHAR(255) NOT NULL,
    message             TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','SIGNED','DECLINED')),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at        TIMESTAMP
);

CREATE UNIQUE INDEX uq_pending_signature_request
    ON signature_requests (evidence_id, requested_user_id) WHERE status = 'PENDING';
CREATE INDEX idx_signature_requests_user ON signature_requests (requested_user_id, status);
CREATE INDEX idx_evidence_signatures_evidence ON evidence_signatures (evidence_id);
