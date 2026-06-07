-- ── users ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users  (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL DEFAULT '',
    full_name     VARCHAR(255),
    role          VARCHAR(50)  NOT NULL,
    auth_provider VARCHAR(50)  NOT NULL DEFAULT 'JWT',
    verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_role    CHECK (role IN ('CLIENT', 'AUDITOR', 'ADMIN'))
);

-- ── otp_verification ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS otp_verification  (
    id       BIGSERIAL    PRIMARY KEY,
    email    VARCHAR(255) NOT NULL,
    otp      VARCHAR(10)  NOT NULL,
    verified BOOLEAN      NOT NULL DEFAULT FALSE,
    expiry   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_otp_email ON otp_verification (email);

-- ── client_profile ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS client_profile (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    email_address VARCHAR(255),
    phone         VARCHAR(50),
    address       VARCHAR(255),
    company_name  VARCHAR(255)
);

CREATE INDEX idx_client_email ON client_profile (email_address);

-- ── auditor_profile ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auditor_profile  (
    id                   BIGSERIAL    PRIMARY KEY,
    first_name           VARCHAR(100),
    last_name            VARCHAR(100),
    email_address        VARCHAR(255),
    phone                VARCHAR(50),
    certification_number VARCHAR(100)
);

CREATE INDEX idx_auditor_email ON auditor_profile (email_address);
