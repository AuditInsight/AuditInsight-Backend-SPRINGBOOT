-- Email re-verification before signing. Each time someone signs a document, they first get a one-time code by
-- email for that document and enter it, proving they still control the account's email address.
-- Kept separate from otp_verification, which holds the account-activation state that login depends on.

CREATE TABLE signing_verifications (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    evidence_id UUID        NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    code_hash   CHAR(64)    NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    expires_at  TIMESTAMP   NOT NULL,
    consumed_at TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_signing_verifications_user_evidence ON signing_verifications (user_id, evidence_id, created_at DESC);
