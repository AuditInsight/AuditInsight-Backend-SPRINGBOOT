-- Audit trail for platform-admin mutations (user activate/deactivate, subscription tier
-- changes, plan price edits). Shared by issues #30 and #31.

CREATE TABLE admin_audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id   BIGINT       NOT NULL REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50)  NOT NULL,
    target_id       VARCHAR(100) NOT NULL,
    before_state    TEXT,
    after_state     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_audit_log_target ON admin_audit_log (target_type, target_id);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log (created_at);
