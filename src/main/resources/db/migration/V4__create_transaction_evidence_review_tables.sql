
CREATE TABLE transactions (
    id               VARCHAR(20) PRIMARY KEY,
    organisation_id  UUID          NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name             VARCHAR(255)  NOT NULL,
    date             DATE          NOT NULL,
    amount           NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    type             VARCHAR(20)   NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    payment_method   VARCHAR(20)   NOT NULL CHECK (payment_method IN ('BANK','MOBILE_MONEY','CASH')),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','COMPLETED')),
    evidence_status  VARCHAR(20)   NOT NULL DEFAULT 'MISSING'
                         CHECK (evidence_status IN ('MISSING','PARTIAL','COMPLETE')),
    created_by       BIGINT        NOT NULL REFERENCES users(id),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE evidence (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    transaction_id  VARCHAR(20)  NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    document_name   VARCHAR(255) NOT NULL,
    folder          VARCHAR(100) NOT NULL,
    subfolder       VARCHAR(100) NOT NULL,
    file_upload     VARCHAR(1000) NOT NULL,
    file_type       VARCHAR(50)  NOT NULL,
    notes           TEXT,
    uploaded_by     BIGINT       NOT NULL REFERENCES users(id),
    uploaded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ── Review Queue ───────────────────────────────────────────────────────────────
CREATE TABLE review_queue (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    transaction_id  VARCHAR(20) NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    issue_type      VARCHAR(30) NOT NULL
                        CHECK (issue_type IN ('MISSING_EVIDENCE','COMPLIANCE_ISSUE','RISK_FLAG','VERIFICATION_PROBLEM')),
    description     TEXT        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','RESOLVED','ESCALATED')),
    flagged_by      VARCHAR(255) NOT NULL,
    resolved_by     BIGINT      REFERENCES users(id),
    resolution_note TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP
);
