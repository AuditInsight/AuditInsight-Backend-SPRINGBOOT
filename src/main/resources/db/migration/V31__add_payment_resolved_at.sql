-- When a payment reached its final outcome (paid, failed, underpaid or expired), so the Super Admin can see the moment
-- a customer actually paid rather than only when they started. Stored like the other payment times (server clock).
ALTER TABLE payments ADD COLUMN resolved_at TIMESTAMP;

-- Existing finished payments: their last update is when they were resolved.
UPDATE payments SET resolved_at = updated_at WHERE status <> 'PENDING' AND resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments (created_at DESC);
