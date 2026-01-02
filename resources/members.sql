-- Members table
CREATE TABLE IF NOT EXISTS members (
  id INTEGER PRIMARY KEY,
  full_name TEXT,
  balance_vnd INTEGER DEFAULT 0,
  birthdate TEXT,
  expiry_date TEXT,
  card_uid TEXT,
  rsa_public_key TEXT,
  transaction_history TEXT,
  pinretry INTEGER DEFAULT 5,
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_members_card_uid ON members(card_uid);

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  member_id INTEGER NOT NULL,
  type TEXT NOT NULL, -- TOPUP, PURCHASE, RENEW
  amount INTEGER NOT NULL,
  items TEXT, -- JSON for purchase/renew details
  payment_method TEXT, -- QR or CASH for topup
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(member_id) REFERENCES members(id)
);

CREATE INDEX IF NOT EXISTS idx_transactions_member ON transactions(member_id);
