CREATE TABLE plans (
    tier                       VARCHAR(20)   PRIMARY KEY CHECK (tier IN ('FREE','STARTER','PROFESSIONAL','ENTERPRISE')),
    description                VARCHAR(255)  NOT NULL,
    monthly_price_usd          NUMERIC(10,2) NOT NULL,
    yearly_price_usd           NUMERIC(10,2) NOT NULL,
    max_users                  INT,
    audits_per_month           INT,
    storage_gb                 INT           NOT NULL,
    basic_reports               BOOLEAN NOT NULL DEFAULT FALSE,
    email_support               BOOLEAN NOT NULL DEFAULT FALSE,
    advanced_reports            BOOLEAN NOT NULL DEFAULT FALSE,
    audit_trail                 BOOLEAN NOT NULL DEFAULT FALSE,
    priority_support             BOOLEAN NOT NULL DEFAULT FALSE,
    custom_workflows             BOOLEAN NOT NULL DEFAULT FALSE,
    compliance_templates         BOOLEAN NOT NULL DEFAULT FALSE,
    api_access                   BOOLEAN NOT NULL DEFAULT FALSE,
    support_24_7                 BOOLEAN NOT NULL DEFAULT FALSE,
    sso_saml                     BOOLEAN NOT NULL DEFAULT FALSE,
    dedicated_account_manager    BOOLEAN NOT NULL DEFAULT FALSE,
    custom_integrations          BOOLEAN NOT NULL DEFAULT FALSE,
    sla_guarantee                BOOLEAN NOT NULL DEFAULT FALSE
);

-- Placeholder USD prices — must be confirmed with the product owner before launch.
INSERT INTO plans (tier, description, monthly_price_usd, yearly_price_usd, max_users, audits_per_month, storage_gb,
                    basic_reports, email_support, advanced_reports, audit_trail, priority_support, custom_workflows,
                    compliance_templates, api_access, support_24_7, sso_saml, dedicated_account_manager,
                    custom_integrations, sla_guarantee)
VALUES
    ('FREE', 'Get started with basic audit management', 0, 0, 2, 5, 1,
     TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('STARTER', 'For small teams getting serious about audits', 29, 290, 10, 50, 10,
     TRUE, TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('PROFESSIONAL', 'Full-featured audit management for growing organizations', 79, 790, 50, NULL, 100,
     TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE),
    ('ENTERPRISE', 'Enterprise-grade security and compliance', 199, 1990, NULL, NULL, 1000,
     TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE);

CREATE TABLE subscriptions (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID          NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    plan_tier       VARCHAR(20)   NOT NULL REFERENCES plans(tier),
    billing_cycle   VARCHAR(10)   NOT NULL CHECK (billing_cycle IN ('MONTHLY','YEARLY')),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','ACTIVE','EXPIRED','CANCELLED')),
    start_date      TIMESTAMP,
    end_date        TIMESTAMP,
    created_by      BIGINT        REFERENCES users(id),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- Only one ACTIVE subscription per organisation at a time.
CREATE UNIQUE INDEX uq_subscriptions_active_per_org
    ON subscriptions (organisation_id) WHERE status = 'ACTIVE';

CREATE TABLE payments (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID          NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    plan_tier           VARCHAR(20)   NOT NULL REFERENCES plans(tier),
    billing_cycle       VARCHAR(10)   NOT NULL CHECK (billing_cycle IN ('MONTHLY','YEARLY')),
    provider            VARCHAR(10)   NOT NULL CHECK (provider IN ('MOMO','CARD')),
    status              VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','SUCCESSFUL','FAILED')),
    usd_amount          NUMERIC(10,2) NOT NULL,
    exchange_rate       NUMERIC(18,6) NOT NULL,
    charged_currency    VARCHAR(3)    NOT NULL,
    charged_amount      NUMERIC(18,2) NOT NULL,
    provider_reference  VARCHAR(100)  UNIQUE,
    payer_phone         VARCHAR(20),
    subscription_id     UUID         REFERENCES subscriptions(id),
    created_by          BIGINT        REFERENCES users(id),
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
