-- Paid by default, with Super Admin-controlled free trials.
--
-- Organisations get workspace access from (in order): a current paid period, an explicitly granted trial, or the
-- platform-wide emergency override. Nothing grants a trial automatically: a trial exists only as a trial_grants
-- row created by a Super Admin, either individually or through an enabled campaign at registration.
--
-- Times use TIMESTAMP (server clock), like the existing subscriptions table, so all access comparisons agree.
-- Existing rows are not modified: old automatic TRIAL subscriptions stay as history and no longer grant anything.

-- ── Campaigns: authorise trials for eligible NEW organisations while enabled ─────────────────────────────
CREATE TABLE trial_campaigns (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    duration_days INT          NOT NULL CHECK (duration_days BETWEEN 1 AND 365),
    starts_at     TIMESTAMP    NOT NULL,
    ends_at       TIMESTAMP,
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by    BIGINT       NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT chk_trial_campaign_window CHECK (ends_at IS NULL OR ends_at > starts_at)
);

-- At most one campaign can be enabled at a time.
CREATE UNIQUE INDEX uq_trial_campaigns_one_enabled ON trial_campaigns ((enabled)) WHERE enabled;

-- ── Trial grants: the only thing that gives an organisation free (trial) access ──────────────────────────
CREATE TABLE trial_grants (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    owner_client_id UUID        NOT NULL REFERENCES client_profile(id),
    source          VARCHAR(20) NOT NULL CHECK (source IN ('INDIVIDUAL', 'CAMPAIGN')),
    campaign_id     UUID        REFERENCES trial_campaigns(id),
    -- ACTIVE: granted and not revoked (it still stops granting access once ends_at has passed);
    -- ENDED: closed after expiry so a new grant can be made; REVOKED: withdrawn by a Super Admin.
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED', 'REVOKED')),
    duration_days   INT         NOT NULL CHECK (duration_days > 0),
    starts_at       TIMESTAMP   NOT NULL,
    ends_at         TIMESTAMP   NOT NULL,
    granted_by      BIGINT      NOT NULL REFERENCES users(id),
    reason          TEXT,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    revoked_at      TIMESTAMP,
    revoked_by      BIGINT      REFERENCES users(id),
    revoke_reason   TEXT,
    -- Set once the "trial ending soon" email has gone out.
    reminder_sent   BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_trial_grant_period CHECK (ends_at > starts_at),
    CONSTRAINT chk_trial_grant_campaign CHECK (source <> 'CAMPAIGN' OR campaign_id IS NOT NULL)
);

-- One open (ACTIVE) grant per organisation; extending or shortening edits that row.
CREATE UNIQUE INDEX uq_trial_grants_one_open_per_org ON trial_grants (organisation_id) WHERE status = 'ACTIVE';
-- An owner can receive a campaign trial only once, however many organisations they create.
CREATE UNIQUE INDEX uq_trial_grants_one_campaign_trial_per_owner ON trial_grants (owner_client_id) WHERE source = 'CAMPAIGN';
CREATE INDEX idx_trial_grants_ends_at ON trial_grants (status, ends_at);

-- ── Trial history: append-only record of every change to a grant ─────────────────────────────────────────
CREATE TABLE trial_grant_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    grant_id        UUID        NOT NULL REFERENCES trial_grants(id) ON DELETE CASCADE,
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    event_type      VARCHAR(30) NOT NULL CHECK (event_type IN
                        ('GRANTED', 'EXTENDED', 'SHORTENED', 'REVOKED', 'ENDED', 'PAID_PERIODS_MOVED')),
    old_ends_at     TIMESTAMP,
    new_ends_at     TIMESTAMP,
    admin_user_id   BIGINT      REFERENCES users(id),
    reason          TEXT,
    details         TEXT,
    created_at      TIMESTAMP   NOT NULL
);

CREATE INDEX idx_trial_grant_events_org ON trial_grant_events (organisation_id, created_at);

-- ── Where each paid period came from ─────────────────────────────────────────────────────────────────────
-- PAYMENT: bought with a verified pawaPay payment; ADMIN_COMPLIMENTARY: granted by a Super Admin;
-- LEGACY: created before this distinction existed (left as is).
ALTER TABLE subscriptions
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'LEGACY'
        CHECK (source IN ('PAYMENT', 'ADMIN_COMPLIMENTARY', 'LEGACY')),
    ADD COLUMN admin_reason TEXT;

-- The plan an organisation chose during onboarding, so it isn't lost if payment is completed later.
ALTER TABLE organisation
    ADD COLUMN requested_subscription_type VARCHAR(20)
        CHECK (requested_subscription_type IN ('MONTHLY', 'SIX_MONTHS', 'ANNUAL'));

-- The emergency override must carry a stated reason.
ALTER TABLE platform_access_settings ADD COLUMN reason TEXT;
