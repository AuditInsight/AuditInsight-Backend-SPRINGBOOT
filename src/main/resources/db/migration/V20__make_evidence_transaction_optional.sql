-- Evidence no longer has to be linked to a transaction — e.g. a standalone document
-- uploaded purely to be signed (the Signature tab) isn't tied to any transaction.
-- The FK to transactions(id) is left in place; it simply doesn't apply to NULL rows.
ALTER TABLE evidence ALTER COLUMN transaction_id DROP NOT NULL;
