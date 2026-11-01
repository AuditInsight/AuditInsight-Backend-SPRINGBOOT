CREATE TABLE platform_access_settings (
    setting_key VARCHAR(32) PRIMARY KEY CHECK (setting_key = 'global'),
    free_access_started_at TIMESTAMP NULL,
    free_access_ends_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by BIGINT REFERENCES users(id),
    CONSTRAINT chk_platform_free_access_dates CHECK (
        (free_access_started_at IS NULL AND free_access_ends_at IS NULL)
        OR (free_access_started_at IS NOT NULL AND free_access_ends_at IS NOT NULL)
    )
);

INSERT INTO platform_access_settings (setting_key, updated_at)
VALUES ('global', CURRENT_TIMESTAMP);
