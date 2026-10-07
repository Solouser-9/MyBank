-- 002: per-user Ed25519 signing keys for statement integrity seals.
-- Run ONCE. No backfill: keypairs are generated lazily on the user's
-- first statement download (StatementSigner.ensureKeyPair).

ALTER TABLE users ADD COLUMN IF NOT EXISTS signing_private_key TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS signing_public_key  TEXT;