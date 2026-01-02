-- Members table schema (portable SQL: works on SQLite/MySQL/PostgreSQL with minor adjustments)
-- Stores: name, balance, birthdate, expiry, card UID, RSA public key, transaction history, pinretry

-- NOTE:
-- - transaction_history is stored as TEXT; you can store JSON here.
-- - expiry can be stored as either an end date or days remaining; here we store an end date.
-- - Admin can read directly without authentication: enforced at application level; DB grants should allow read access.

CREATE TABLE IF NOT EXISTS members (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name       VARCHAR(100) NOT NULL,
    balance_vnd     INTEGER NOT NULL DEFAULT 0,
    birthdate       DATE NOT NULL,
    expiry_date     DATE NOT NULL,
    card_uid        VARCHAR(64) NOT NULL UNIQUE,
    rsa_public_key  TEXT NOT NULL,
    transaction_history TEXT,
    pinretry        SMALLINT NOT NULL DEFAULT 5,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_members_card_uid ON members(card_uid);
CREATE INDEX IF NOT EXISTS idx_members_full_name ON members(full_name);

-- Optional: simple view that exposes all fields for admin convenience
CREATE VIEW IF NOT EXISTS admin_members_view AS
SELECT id, full_name, balance_vnd, birthdate, expiry_date,
       card_uid, rsa_public_key, transaction_history, pinretry,
       created_at, updated_at
FROM members;

-- Example insert
-- INSERT INTO members (full_name, balance_vnd, birthdate, expiry_date, card_uid, rsa_public_key, transaction_history, pinretry)
-- VALUES (
--   'Nguyen Van A', 500000, DATE '1995-05-20', DATE '2026-12-31',
--   '04A1B2C3D4E5', '-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkq...\n-----END PUBLIC KEY-----',
--   '{"transactions":[{"type":"topup","amount":200000,"time":"2026-01-03T10:15:00Z"}]}',
--   5
-- );
