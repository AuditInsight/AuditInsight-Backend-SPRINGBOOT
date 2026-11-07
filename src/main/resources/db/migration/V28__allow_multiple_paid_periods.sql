-- One paid period per payment: an organisation can now have a running period plus periods queued after it
-- (renewing early, or paying during a trial). Overlap is prevented in the application, which creates periods
-- back to back under a per-organisation advisory lock, so the old one-ACTIVE-row-per-organisation index goes.
DROP INDEX IF EXISTS uq_subscriptions_active_per_org;

CREATE INDEX IF NOT EXISTS idx_subscriptions_org_status_end ON subscriptions (organisation_id, status, end_date);
CREATE INDEX IF NOT EXISTS idx_payments_org_status ON payments (organisation_id, status);
