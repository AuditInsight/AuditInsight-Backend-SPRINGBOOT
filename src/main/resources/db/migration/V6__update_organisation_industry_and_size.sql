-- ── organisation: industry is now the SME/NGO enum field itself ─────────
--    (supersedes the separate organisation_type column added in V5)
ALTER TABLE organisation DROP COLUMN IF EXISTS organisation_type;

UPDATE organisation SET industry = 'PRIVATE' WHERE industry IS NULL OR industry NOT IN ('PRIVATE','NGO');
ALTER TABLE organisation ALTER COLUMN industry SET NOT NULL;
ALTER TABLE organisation ADD CONSTRAINT chk_organisation_industry CHECK (industry IN ('PRIVATE','NGO'));

-- ── organisation: size is required ───────────────────────────────────────
UPDATE organisation SET size = '1-10' WHERE size IS NULL;
ALTER TABLE organisation ALTER COLUMN size SET NOT NULL;
