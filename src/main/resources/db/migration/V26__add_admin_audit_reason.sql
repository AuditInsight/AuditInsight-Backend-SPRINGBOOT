-- The administrator's stated reason for an action, kept in its own column so it can be searched and shown.
-- Required by the application for trial grants, trial campaigns and emergency access; optional elsewhere.
ALTER TABLE admin_audit_log ADD COLUMN reason TEXT;
