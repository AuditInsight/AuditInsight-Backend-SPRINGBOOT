-- Makes subscription prices/durations platform-admin editable (issue #31) instead of
-- hardcoded in the SubscriptionType enum. Seeded with the enum's current values.

CREATE TABLE plan_prices (
    subscription_type VARCHAR(20)   PRIMARY KEY
                           CHECK (subscription_type IN ('MONTHLY','SIX_MONTHS','ANNUAL')),
    price             NUMERIC(18,2) NOT NULL CHECK (price > 0),
    currency          VARCHAR(3)    NOT NULL DEFAULT 'RWF',
    duration_days     INT           NOT NULL CHECK (duration_days > 0),
    updated_by        BIGINT        REFERENCES users(id),
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO plan_prices (subscription_type, price, currency, duration_days, updated_by, updated_at)
VALUES
    ('MONTHLY',     15000,  'RWF', 30,  NULL, CURRENT_TIMESTAMP),
    ('SIX_MONTHS',  80000,  'RWF', 180, NULL, CURRENT_TIMESTAMP),
    ('ANNUAL',      150000, 'RWF', 365, NULL, CURRENT_TIMESTAMP);
