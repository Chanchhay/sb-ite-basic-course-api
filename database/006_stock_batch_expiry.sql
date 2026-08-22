-- 006 — Rotate stock by expiry, not just by arrival.
--
-- A batch now carries the supplier's lot, when it was made, and when it goes
-- off, and the consumption queue is ordered by that expiry before anything
-- else. Arrival order is the wrong rotation for anything perishable: a
-- delivery that came in this morning can be short-dated and have to leave
-- before stock that has been on the shelf a fortnight.
--
-- Hibernate adds the three columns itself under `ddl-auto: update`, so this
-- script is only here for the index behind that ordering — Hibernate never
-- creates one for a sort it does not know about, and every sale reads this
-- query. Without it the queue degrades into a sort of every open batch in the
-- business on each movement.
--
-- Run after the app has booted once on the new code, so the columns exist.
-- Safe to run more than once.

BEGIN;

-- The exact order `findOpenLayers` asks for. Postgres already sorts nulls last
-- on an ascending column, which is precisely the rule wanted here: a batch with
-- no expiry queues behind every batch that has one. So the plain column order
-- matches the query's `expires_at asc nulls last` and the planner can read the
-- queue straight off the index instead of sorting.
--
-- Partial, because an emptied batch is never in the queue again and there are
-- far more of those than open ones on any shop that has been trading a while.
CREATE INDEX IF NOT EXISTS idx_stock_layers_rotation
    ON stock_layers (business_owner_id, expires_at, received_at, id)
    WHERE quantity_remaining > 0;

-- Answering a recall: which batches of this lot did we ever hold, and what is
-- left of them. Only rows that named a lot are worth indexing.
CREATE INDEX IF NOT EXISTS idx_stock_layers_lot_number
    ON stock_layers (business_owner_id, lot_number)
    WHERE lot_number IS NOT NULL;

COMMIT;

-- What is already past its date and still on the shelf, worth writing off:
--
--   SELECT l.item_id,
--          l.lot_number,
--          l.expires_at,
--          l.quantity_remaining,
--          l.quantity_remaining * l.unit_cost AS worth
--   FROM stock_layers l
--   WHERE l.quantity_remaining > 0
--     AND l.expires_at < CURRENT_DATE
--   ORDER BY l.expires_at;
--
-- Batches that arrived before this change have no expiry and queue behind
-- everything dated, which is the safe reading: nothing is known to go off, so
-- nothing is rushed out ahead of stock that is.
