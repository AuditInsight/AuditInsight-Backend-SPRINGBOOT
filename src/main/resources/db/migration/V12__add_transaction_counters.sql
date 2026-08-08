-- Per-organisation atomic counter for human-readable transaction IDs (TXN-0001, TXN-0002, ...).
-- Replaces the previous COUNT(*)-then-format approach, which raced under concurrent inserts and
-- produced duplicate IDs (transactions_pkey violations).
CREATE TABLE transaction_counters (
    organisation_id UUID PRIMARY KEY REFERENCES organisation(id) ON DELETE CASCADE,
    next_seq        INT  NOT NULL DEFAULT 0
);

-- Seed each organisation's counter at its current transaction count, so numbering continues
-- from where it left off instead of restarting at TXN-0001 and colliding with existing rows.
INSERT INTO transaction_counters (organisation_id, next_seq)
SELECT organisation_id, COUNT(*) FROM transactions GROUP BY organisation_id;