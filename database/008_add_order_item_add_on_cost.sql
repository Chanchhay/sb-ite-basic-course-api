-- 008 — Give an add-on line somewhere to record what it cost.
--
-- An add-on consumed with a sale takes stock and has a real FIFO cost, but
-- that cost was never added to anything: `recordAddOnSale` was called and its
-- return value dropped. Every sale that carried an extra therefore reported a
-- cost that left the extra out, and a margin that was better than the one it
-- made.
--
-- The cost now lands on the add-on line itself, beside the price the line was
-- already charging for it, so the item report and the statement reach the same
-- figure rather than two.
--
-- Hibernate cannot add this column on any database that has already sold an
-- add-on: Postgres refuses a NOT NULL column with no default on a table with
-- rows, and Hibernate does not supply one. It logs the failure and boots
-- anyway, which is exactly how a column goes quietly missing for weeks. This
-- adds it properly.
--
-- Safe to run again.
--
-- Deliberately no backfill. Zero is the honest value for a line sold before
-- this: those sales recorded no add-on cost in their own totals either, so the
-- books still agree with themselves — they simply both understate what the
-- extras cost. Inventing a figure now would make the two disagree, and would
-- rewrite a financial record from a guess rather than from what was kept.

BEGIN;

ALTER TABLE order_item_add_ons
    ADD COLUMN IF NOT EXISTS cost NUMERIC(14, 2) NOT NULL DEFAULT 0;

COMMIT;

-- Sales whose add-on cost predates this, and are therefore understated by
-- whatever those extras cost:
--
--   SELECT count(DISTINCT s.id) AS sales_without_add_on_cost
--   FROM sales s
--   JOIN order_items oi ON oi.order_id = s.order_id
--   JOIN order_item_add_ons a ON a.order_item_id = oi.id
--   WHERE a.cost = 0;
--
-- The movements are still on record if you ever want to price them: each one
-- is a SALE entry in `stock_entries` with an `add_on_id` and the order in
-- `reference_id`, carrying its own `cost_of_goods`.
