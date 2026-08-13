-- 003 — Let a stock movement be against an add-on.
--
-- Stock used to be counted for items only, so `item_id` was NOT NULL on both
-- the ledger and its FIFO batches. An add-on is counted the same way now — a
-- tub of pearls empties whether it was scooped into one drink or ten — and it
-- is not an item, so it sits in its own column and `item_id` has to be allowed
-- to be empty.
--
-- Hibernate adds the new `add_on_id` columns itself under `ddl-auto: update`,
-- but it never relaxes a constraint it already created, so without this an
-- add-on movement fails on a not-null violation.
--
-- Run after the app has booted once on the new code, so `add_on_id` exists.
-- Safe to run more than once.

BEGIN;

ALTER TABLE stock_entries ALTER COLUMN item_id DROP NOT NULL;
ALTER TABLE stock_layers  ALTER COLUMN item_id DROP NOT NULL;

-- Exactly one target per row. The service enforces this, but a wrong row here
-- would be a balance that belongs to nothing and cannot be traced back.
DO $$
BEGIN
    -- The columns arrive with Hibernate; skip quietly if it has not run yet.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'stock_entries' AND column_name = 'add_on_id'
    ) THEN
        RAISE NOTICE 'add_on_id not present yet — boot the app, then re-run.';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'stock_entries'::regclass
          AND conname = 'ck_stock_entries_one_target'
    ) THEN
        ALTER TABLE stock_entries
            ADD CONSTRAINT ck_stock_entries_one_target
            CHECK ((item_id IS NULL) <> (add_on_id IS NULL));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'stock_layers'::regclass
          AND conname = 'ck_stock_layers_one_target'
    ) THEN
        ALTER TABLE stock_layers
            ADD CONSTRAINT ck_stock_layers_one_target
            CHECK ((item_id IS NULL) <> (add_on_id IS NULL));
    END IF;
END $$;

COMMIT;

-- Check afterwards — both should report YES:
--
--   SELECT is_nullable FROM information_schema.columns
--   WHERE table_name IN ('stock_entries', 'stock_layers') AND column_name = 'item_id';
