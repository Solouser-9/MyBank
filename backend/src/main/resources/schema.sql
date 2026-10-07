
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  email VARCHAR(120) UNIQUE NOT NULL,
  phone VARCHAR(20) UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  pin_hash VARCHAR(100),
  pin_attempts INT DEFAULT 0,
  pin_locked_until TIMESTAMPTZ,
  kyc_status VARCHAR(20) DEFAULT 'PENDING',
  token_version INT DEFAULT 0,
  signing_private_key TEXT,
  signing_public_key TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE wallets (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  balance_kobo BIGINT NOT NULL DEFAULT 0
);


CREATE TABLE otp_codes (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  purpose VARCHAR(30) NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  attempts INT DEFAULT 0,
  used BOOLEAN DEFAULT FALSE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  token_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN DEFAULT FALSE,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE beneficiaries (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  name VARCHAR(100),
  bank_code VARCHAR(10),
  account_number VARCHAR(10),
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE virtual_accounts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  provider VARCHAR(20) NOT NULL,
  account_number VARCHAR(10) NOT NULL,
  bank_name VARCHAR(50),
  provider_ref VARCHAR(100),
  status VARCHAR(20) DEFAULT 'active',
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (provider, account_number)
);


CREATE TABLE provider_events (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(20) NOT NULL,
  event_id VARCHAR(100) NOT NULL,
  event_type VARCHAR(50),
  payload JSONB NOT NULL,
  processed BOOLEAN DEFAULT FALSE,
  received_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (provider, event_id)
);


CREATE TABLE ledger_entries (
  id BIGSERIAL PRIMARY KEY,
  txn_ref UUID NOT NULL,
  account VARCHAR(50) NOT NULL,
  direction VARCHAR(6) NOT NULL CHECK (direction IN ('debit','credit')),
  amount_kobo BIGINT NOT NULL CHECK (amount_kobo > 0),
  narration TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE wallet_transactions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  reference VARCHAR(60) UNIQUE NOT NULL,
  type VARCHAR(20) NOT NULL,
  amount_kobo BIGINT NOT NULL,
  fee_kobo BIGINT DEFAULT 0,
  balance_after_kobo BIGINT NOT NULL,
  status VARCHAR(20) DEFAULT 'pending'
         CHECK (status IN ('pending','successful','failed','reversed')),
  category VARCHAR(20)
         CHECK (category IN ('internal','external','bills','funding')),
  counterparty TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  message VARCHAR(200) NOT NULL,
  read BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT,
  actor_type VARCHAR(10) DEFAULT 'user',
  action VARCHAR(50) NOT NULL,
  entity VARCHAR(60),
  details JSONB,
  ip VARCHAR(45),
  device VARCHAR(100),
  result VARCHAR(10),
  request_id VARCHAR(40),
  created_at TIMESTAMPTZ DEFAULT now()
);


CREATE INDEX IF NOT EXISTS idx_wallet_txn_user      ON wallet_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user   ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account       ON ledger_entries(account);
CREATE INDEX IF NOT EXISTS idx_otp_lookup           ON otp_codes(user_id, purpose);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user  ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_events_unprocessed   ON provider_events(processed, received_at);


REVOKE UPDATE, DELETE ON audit_logs FROM PUBLIC;