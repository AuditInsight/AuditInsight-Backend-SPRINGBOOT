-- ── 1. Allow MEMBER role in users table (invited members are persisted here) ─────
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('CLIENT', 'AUDITOR', 'ADMIN', 'MEMBER'));

-- ── 2. Track forced password-change for accounts created via invitation ──────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- ── 3. Clear all organisation data so column types can be changed cleanly ────────
--      CASCADE propagates the truncation to organisation_member,
--      organisation_currency, and organisation_invitation (all have ON DELETE CASCADE FKs).
TRUNCATE TABLE organisation RESTART IDENTITY CASCADE;

-- ── 4. Fix organisation.client_id: BIGINT → UUID referencing client_profile(id) ──
ALTER TABLE organisation DROP COLUMN client_id;
ALTER TABLE organisation ADD COLUMN client_id UUID NOT NULL REFERENCES client_profile(id);

-- ── 5. Fix organisation_invitation.invited_by: BIGINT → UUID → client_profile(id)─
ALTER TABLE organisation_invitation DROP COLUMN invited_by;
ALTER TABLE organisation_invitation ADD COLUMN invited_by UUID NOT NULL REFERENCES client_profile(id);