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
--
-- Amended: the constraint alone was not enough. A database could already hold
-- layers and entries pointing at neither an item nor an add-on, and one such
-- row is all it takes for ADD CONSTRAINT to fail — so on any database with one
-- this script had quietly never applied at all. Untargeted rows that nothing
-- has drawn stock from are now cleared first.

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

    -- Rows that already belong to nothing, from before there was a constraint
    -- to stop them being written. They cannot be traced back to an item or an
    -- add-on, so there is nothing to repair them into — and left in place a
    -- single one of them fails the check below for the whole table.
    --
    -- Only untargeted rows, and only ones nothing has drawn stock from. A
    -- layer with consumptions against it is a real balance whose target was
    -- lost, and that is a repair to make by hand with the invoices in front of
    -- you, not a row to delete on boot.
    DELETE FROM stock_layers l
    WHERE l.item_id IS NULL
      AND l.add_on_id IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM stock_consumptions c WHERE c.stock_layer_id = l.id
      );

    DELETE FROM stock_entries e
    WHERE e.item_id IS NULL
      AND e.add_on_id IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM stock_consumptions c WHERE c.stock_entry_id = e.id
      );

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
