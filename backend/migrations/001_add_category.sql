-- 001: transaction categories — set at write time, replaces the donut heuristic.
-- Run ONCE. Safe against live data: nullable column, backfill covers all rows,
-- then the CHECK is added (NULLs always pass CHECK constraints anyway).

ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS category VARCHAR(20);

-- One-time backfill — the heuristic retires into history here.
-- (Matches the exact counterparty formats the write paths have always produced.)
UPDATE wallet_transactions SET category = 'bills'
 WHERE type = 'debit' AND counterparty ~ '^(airtime|data|electricity|tv|cable):';

UPDATE wallet_transactions SET category = 'internal'
 WHERE counterparty ~ '^User [0-9]+$';

UPDATE wallet_transactions SET category = 'external'
 WHERE type = 'debit' AND category IS NULL;

UPDATE wallet_transactions SET category = 'funding'
 WHERE type = 'credit' AND category IS NULL;

ALTER TABLE wallet_transactions
  ADD CONSTRAINT wallet_transactions_category_check
  CHECK (category IN ('internal', 'external', 'bills', 'funding'));