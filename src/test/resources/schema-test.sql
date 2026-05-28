CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255),
    full_name  VARCHAR(255),
    role       VARCHAR(50)  NOT NULL,
    auth_provider VARCHAR(50),
    verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS otp_verification (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    email    VARCHAR(255) NOT NULL,
    otp      VARCHAR(10)  NOT NULL,
    verified BOOLEAN      NOT NULL DEFAULT FALSE,
    expiry   TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS client_profile (
    id            UUID         PRIMARY KEY DEFAULT RANDOM_UUID(),
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    email_address VARCHAR(255),
    phone         VARCHAR(50),
    address       VARCHAR(255),
    company_name  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS auditor_profile (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name           VARCHAR(100),
    last_name            VARCHAR(100),
    email_address        VARCHAR(255),
    phone                VARCHAR(50),
    certification_number VARCHAR(100)
);
