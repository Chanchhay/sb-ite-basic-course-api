-- 010 — Drop `compare_at_price` from items.
--
-- The column was a manually set "was" price, shown struck through beside the
-- selling price. Nothing in the back office ever gained a field to set it: the
-- item form has no input, and the only value that ever reached it would have
-- come from an import column or a direct API call. It has been carried along
-- ever since — restated on every base-currency change, round-tripped by the
-- item form so an edit would not wipe it — for a number nobody could enter.
--
-- The struck-through price on the storefront is not lost. It is computed:
-- StorefrontServiceImpl fills `compareAtPrice` on the response with the real
-- pre-discount price whenever a discount applies, which is the only figure that
-- can honestly be shown crossed out. That path never read this column.
--
-- Hibernate's `ddl-auto: update` adds and widens, never drops, so removing the
-- field from the entity leaves the column sitting in the table with its data.
-- This is what removes it.
--
-- Irreversible: the values go with the column. That is the intent — they are
-- prices no screen can show and no screen could set — but a database taken
-- before this runs is the only way back.
--
-- Run after deploying code that no longer maps the field. Running it first is
-- also safe: the old code would simply fail to find the column on its next
-- write. Safe to run more than once.

BEGIN;

ALTER TABLE items
    DROP COLUMN IF EXISTS compare_at_price;

COMMIT;
