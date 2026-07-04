-- ── organisation: add size and organisation_type ────────────────────────
ALTER TABLE organisation ADD COLUMN IF NOT EXISTS size VARCHAR(50);
ALTER TABLE organisation ADD COLUMN IF NOT EXISTS organisation_type VARCHAR(10)
    NOT NULL DEFAULT 'SME' CHECK (organisation_type IN ('SME','NGO'));

-- ── transactions: add counterparty ───────────────────────────────────────
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS counterparty VARCHAR(255)
    NOT NULL DEFAULT 'UNKNOWN';