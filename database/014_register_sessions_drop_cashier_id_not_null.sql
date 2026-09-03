-- 014 — Drop the stale NOT NULL on register_sessions.cashier_id.
--
-- RegisterSession was rebuilt around a `userId` field (register_sessions.user_id);
-- `cashier_id` is what it was called before that rename. Nothing in the
-- entity has populated it since, but the column's own NOT NULL constraint
-- outlived the rename, so every new session insert has been failing:
--
--   ERROR: null value in column "cashier_id" of relation "register_sessions"
--          violates not-null constraint
--
-- The column itself is left in place rather than dropped outright — it may
-- still hold history worth keeping on a database that traded before the
-- rename, and dropping a column is not something to do in the same script
-- that is only trying to unblock inserts. Only the constraint that is
-- actually causing the failure comes off.
--
-- Guarded on the column existing at all: a database created from scratch by
-- `ddl-auto: update` never gets `cashier_id` in the first place, so this is
-- a no-op there. Safe to run more than once.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'register_sessions' AND column_name = 'cashier_id'
    ) THEN
        ALTER TABLE register_sessions ALTER COLUMN cashier_id DROP NOT NULL;
    END IF;
END $$;

COMMIT;
