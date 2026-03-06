-- Flyway migration V1: Create account tables
-- PostgreSQL DDL for account creation system

CREATE SCHEMA IF NOT EXISTS acct_owner;

-- Customer table (pre-existing, managed separately)
CREATE TABLE IF NOT EXISTS acct_owner.customer (
    customer_id      VARCHAR(12)  PRIMARY KEY,
    customer_name    VARCHAR(40)  NOT NULL,
    date_of_birth    DATE         NOT NULL,
    status           CHAR(1)      NOT NULL CHECK (status IN ('A', 'I')),
    blacklist_flag   CHAR(1)      NOT NULL CHECK (blacklist_flag IN ('Y', 'N')),
    country_code     CHAR(2)      NOT NULL
);

CREATE INDEX idx_customer_status ON acct_owner.customer(status);
COMMENT ON TABLE acct_owner.customer IS 'Customer master table (reference data)';

-- Account table
CREATE TABLE acct_owner.account (
    account_no       VARCHAR(20)     PRIMARY KEY,
    request_id       VARCHAR(20)     NOT NULL UNIQUE,
    customer_id      VARCHAR(12)     NOT NULL,
    account_type     VARCHAR(3)      NOT NULL CHECK (account_type IN ('SAV', 'CUR', 'LOA')),
    currency         VARCHAR(3)      NOT NULL CHECK (currency IN ('INR', 'USD', 'EUR')),
    open_balance     NUMERIC(13,2)   NOT NULL,
    channel_code     VARCHAR(10)     NOT NULL,
    status           CHAR(1)         NOT NULL CHECK (status IN ('A', 'I')),
    open_ts          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES acct_owner.customer(customer_id)
);

CREATE INDEX idx_account_customer_id ON acct_owner.account(customer_id);
CREATE INDEX idx_account_open_ts ON acct_owner.account(open_ts);
CREATE INDEX idx_account_request_id ON acct_owner.account(request_id);

COMMENT ON TABLE acct_owner.account IS 'Master account table - one row per account';
COMMENT ON COLUMN acct_owner.account.request_id IS 'Idempotency key - unique per account creation request';
COMMENT ON COLUMN acct_owner.account.version IS 'Optimistic locking version';

-- Account audit table
CREATE TABLE acct_owner.account_audit (
    audit_id         BIGSERIAL       PRIMARY KEY,
    request_id       VARCHAR(20)     NOT NULL,
    account_no       VARCHAR(20)     NOT NULL,
    event_type       VARCHAR(20)     NOT NULL,
    event_ts         TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_code      VARCHAR(12)     NOT NULL,
    result_text      VARCHAR(60)     NOT NULL
);

CREATE INDEX idx_audit_request_id ON acct_owner.account_audit(request_id);
CREATE INDEX idx_audit_event_ts ON acct_owner.account_audit(event_ts);
CREATE INDEX idx_audit_event_type ON acct_owner.account_audit(event_type);

COMMENT ON TABLE acct_owner.account_audit IS 'Immutable audit trail for all account creation attempts';
