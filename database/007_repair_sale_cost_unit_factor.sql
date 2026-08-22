-- 007 — Repair the cost of sales sold in packs.
--
-- A sale's `total_cost` was accumulated as `unit_cost * quantity`, but
-- `unit_cost` on a line is what one *base* unit cost — it comes back from the
-- stock movement that took `base_quantity` off the shelf. For anything sold in
-- a pack, a case of twenty-four was therefore costed at the price of one unit:
-- the cost was understated by the pack factor, and every margin, profit figure
-- and channel comparison drawn from it was flattered by the same amount.
--
-- The code now multiplies by the base quantity, so sales taken from here on
-- are right. This repairs the ones already recorded.
--
-- Nothing is invented. `order_items` kept `unit_cost`, `quantity` and
-- `unit_factor` correctly all along — only the sum over them was wrong — so
-- each sale is recomputed from its own lines and lands on what it always
-- should have said.
--
-- Deliberately narrow. Only sales with at least one line whose `unit_factor`
-- is not 1 are touched: every other sale was already correct, and a financial
-- record is not rewritten to prove a script ran. Safe to run again — the
-- second pass finds the figures already equal and updates nothing.

BEGIN;

UPDATE sales s
SET total_cost = recomputed.cost
FROM (
    SELECT oi.order_id,
           round(
               sum(oi.unit_cost * oi.quantity * coalesce(oi.unit_factor, 1)),
               2
           ) AS cost
    FROM order_items oi
    GROUP BY oi.order_id
) recomputed
WHERE s.order_id = recomputed.order_id
  -- Only where a pack was actually involved. An item sold one at a time has
  -- a factor of 1, and its old figure was never wrong.
  AND EXISTS (
      SELECT 1
      FROM order_items oi
      WHERE oi.order_id = s.order_id
        AND coalesce(oi.unit_factor, 1) <> 1
  )
  -- Idempotent: a row already carrying the right number is left alone.
  AND s.total_cost IS DISTINCT FROM recomputed.cost;

COMMIT;

-- What moved, and by how much — run before and after if you want the record:
--
--   SELECT s.invoice_number,
--          s.sold_at,
--          s.total_cost AS recorded,
--          round(sum(oi.unit_cost * oi.quantity
--                    * coalesce(oi.unit_factor, 1)), 2) AS should_be
--   FROM sales s
--   JOIN order_items oi ON oi.order_id = s.order_id
--   GROUP BY s.id, s.invoice_number, s.sold_at, s.total_cost
--   HAVING s.total_cost IS DISTINCT FROM
--          round(sum(oi.unit_cost * oi.quantity
--                    * coalesce(oi.unit_factor, 1)), 2)
--   ORDER BY s.sold_at DESC;
--
-- Profit reported for past periods will fall wherever this corrected a sale.
-- That is the point: the margin it showed before was never earned.
--
-- Not covered here: add-ons consumed with a sale take stock and have a cost,
-- but that cost has never been added to `total_cost` at all. Repairing it
-- means deciding whether an add-on's cost belongs to the line it garnished,
-- which is a change to what the figure means rather than a repair of it.
