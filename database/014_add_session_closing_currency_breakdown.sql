-- 014 — Add closing cash breakdown columns to register_sessions
--
-- Mirrors base_opening_balance and secondary_opening_balance so a drawer
-- counted in multiple currencies stores the per-currency counted cash
-- in addition to the base-converted total.

BEGIN;

ALTER TABLE register_sessions ADD COLUMN IF NOT EXISTS base_actual_amount NUMERIC(12, 2);
ALTER TABLE register_sessions ADD COLUMN IF NOT EXISTS secondary_actual_amount NUMERIC(14, 2);

COMMIT;
