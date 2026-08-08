-- Transaction.id switches from an app-generated sequential string (TXN-0001, ...) to an
-- app-generated java.util.UUID. The transaction_counters-based sequence raced under concurrent
-- inserts and produced duplicate-key violations on transactions_pkey; UUIDs remove the need for
-- any shared counter entirely.
--
-- TXN-0001-style strings cannot be cast to UUID, so existing rows (and the evidence/review_queue
-- foreign keys pointing at them) are preserved by minting a UUID per existing transaction and
-- remapping the FK columns, rather than dropping and recreating the tables.

-- 1. Add UUID replacement columns. The volatile default backfills each existing transaction row
--    with a distinct UUID as part of the column-add rewrite.
ALTER TABLE transactions ADD COLUMN id_uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE evidence ADD COLUMN transaction_id_uuid UUID;
ALTER TABLE review_queue ADD COLUMN transaction_id_uuid UUID;

-- 2. Remap the FK columns to the newly minted UUIDs via the existing string-id linkage.
UPDATE evidence e
SET transaction_id_uuid = t.id_uuid
FROM transactions t
WHERE e.transaction_id = t.id;

UPDATE review_queue r
SET transaction_id_uuid = t.id_uuid
FROM transactions t
WHERE r.transaction_id = t.id;

-- 3. Drop the old FK constraints and string columns, promote the UUID columns in their place.
ALTER TABLE evidence DROP CONSTRAINT evidence_transaction_id_fkey;
ALTER TABLE review_queue DROP CONSTRAINT review_queue_transaction_id_fkey;

ALTER TABLE evidence ALTER COLUMN transaction_id_uuid SET NOT NULL;
ALTER TABLE review_queue ALTER COLUMN transaction_id_uuid SET NOT NULL;

ALTER TABLE evidence DROP COLUMN transaction_id;
ALTER TABLE review_queue DROP COLUMN transaction_id;
ALTER TABLE evidence RENAME COLUMN transaction_id_uuid TO transaction_id;
ALTER TABLE review_queue RENAME COLUMN transaction_id_uuid TO transaction_id;

ALTER TABLE transactions DROP CONSTRAINT transactions_pkey;
ALTER TABLE transactions DROP COLUMN id;
ALTER TABLE transactions RENAME COLUMN id_uuid TO id;
ALTER TABLE transactions ADD PRIMARY KEY (id);

ALTER TABLE evidence ADD CONSTRAINT evidence_transaction_id_fkey
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE;
ALTER TABLE review_queue ADD CONSTRAINT review_queue_transaction_id_fkey
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE;

-- 4. transaction_counters only ever backed the old sequential-string ID generator; the
--    application no longer references it now that IDs are UUIDs generated in code.
DROP TABLE IF EXISTS transaction_counters;
