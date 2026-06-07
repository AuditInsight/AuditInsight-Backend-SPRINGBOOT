-- ── organisation ────────────────────────────────────────────────────────
CREATE TABLE organisation (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id        BIGINT      NOT NULL REFERENCES users(id),
    name             VARCHAR(255) NOT NULL,
    industry         VARCHAR(100),
    fiscal_year_start VARCHAR(5) NOT NULL DEFAULT '01-01',
    fiscal_year_end  VARCHAR(5)  NOT NULL DEFAULT '12-31',
    default_currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

-- ── organisation_member ──────────────────────────────────────────────────
CREATE TABLE organisation_member (
    id              UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID      NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    user_id         BIGINT    NOT NULL REFERENCES users(id),
    role            VARCHAR(20) NOT NULL CHECK (role IN ('CLIENT','MEMBER','AUDITOR')),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','PENDING','REVOKED')),
    joined_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_org_member UNIQUE (organisation_id, user_id)
);

-- ── organisation_currency ────────────────────────────────────────────────
CREATE TABLE organisation_currency (
    id              UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID      NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    currency_code   VARCHAR(10) NOT NULL,
    is_default      BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_org_currency UNIQUE (organisation_id, currency_code)
);

-- ── organisation_invitation ──────────────────────────────────────────────
CREATE TABLE organisation_invitation (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    invited_email   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('MEMBER','AUDITOR')),
    token           VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED')),
    invited_by      BIGINT      NOT NULL REFERENCES users(id),
    expires_at      TIMESTAMP   NOT NULL,
    created_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
