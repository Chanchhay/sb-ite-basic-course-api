-- 013 — Make an offline sale survive the sync, and repair the ones that did not.
--
-- Three things, all from the same feature: sales taken with no connection,
-- queued on the till, and reconciled when it comes back.
--
--
-- 1. `orders.tax_amount` must never be handed a null.
--
-- The sync writes the till's own tax breakdown onto the order. A sale taken
-- with tax switched off has none to send, and the sync was passing that
-- straight through — replacing the entity's own zero with null on a NOT NULL
-- column:
--
--   ERROR: null value in column "tax_amount" of relation "orders"
--          violates not-null constraint
--
-- The service no longer does that. The column default is here as well because
-- 005 only set one on databases where it added the column: a database created
-- from scratch by `ddl-auto: update` has the constraint and no default, so the
-- same mistake anywhere else would be a failed sale rather than a zero. A
-- money column that refuses to be null should still say what null means.
--
--
-- 2. Offline sales carry the time they were taken, not the time they synced.
--
-- `created_date` is @CreatedDate, so Spring stamps it when the row is
-- inserted — which for a queued sale is whenever the till got its connection
-- back. A whole shift's takings landed at one minute, and the Orders and
-- Receipts lists showed them that way while the Sale beside each one held the
-- right time all along.
--
-- New syncs correct the stamp themselves. This repairs the ones already
-- recorded, from the sale's own `sold_at` — only where the two disagree, only
-- for orders whose invoice number is a till-issued offline id, and only where
-- the difference is more than a minute, so a normal sale that happens to have
-- been written a second after its sale time is left alone.
--
--
-- 3. It runs itself, on boot, once — like every script here. Every statement
--    is idempotent, so a database that was repaired by hand adopts it cleanly.
--
--
-- WHAT THIS DOES NOT FIX
--
-- Stock moved by an offline sale that synced before the line detail was
-- carried across — an option coming off the item's own balance, or a pack of
-- ten reducing the shelf by one. Those movements are real ledger entries with
-- costs attached, and rewriting them from here would be guessing at what the
-- shelf held on a day that has passed. Count the affected items in and adjust
-- them through Inventory, which records the correction as a correction.
--
-- The query at the bottom lists the sales that were synced before the fix, so
-- you know which items to look at.

BEGIN;

-- 1 ------------------------------------------------------------------------

UPDATE orders SET tax_amount = 0 WHERE tax_amount IS NULL;
UPDATE sales  SET tax_amount = 0 WHERE tax_amount IS NULL;

ALTER TABLE orders ALTER COLUMN tax_amount SET DEFAULT 0;
ALTER TABLE sales  ALTER COLUMN tax_amount SET DEFAULT 0;

ALTER TABLE orders ALTER COLUMN tax_amount SET NOT NULL;
ALTER TABLE sales  ALTER COLUMN tax_amount SET NOT NULL;

-- `tax_rate` is nullable by design — an order with no tax rule has no rate —
-- but a default of zero keeps the arithmetic honest for anything reading it
-- without a null check.
ALTER TABLE orders ALTER COLUMN tax_rate SET DEFAULT 0;
ALTER TABLE sales  ALTER COLUMN tax_rate SET DEFAULT 0;

-- 2 ------------------------------------------------------------------------

UPDATE orders o
   SET created_date = s.sold_at
  FROM sales s
 WHERE s.order_id = o.id
   AND o.invoice_number LIKE 'offline-%'
   AND s.sold_at IS NOT NULL
   AND o.created_date IS DISTINCT FROM s.sold_at
   AND abs(extract(epoch FROM (o.created_date - s.sold_at))) > 60;

COMMIT;


-- VERIFY -------------------------------------------------------------------
--
-- Expect zero rows from each. These are commented out because the runner
-- applies this file on boot and a boot has nobody to read a result set; run
-- them by hand against the deployed database once it is up.

-- Any order still stamped with its sync time rather than its sale time:
--
--   SELECT o.invoice_number, o.created_date, s.sold_at
--     FROM orders o
--     JOIN sales s ON s.order_id = o.id
--    WHERE o.invoice_number LIKE 'offline-%'
--      AND abs(extract(epoch FROM (o.created_date - s.sold_at))) > 60;

-- Any money column that can still be handed a null:
--
--   SELECT table_name, column_name, is_nullable, column_default
--     FROM information_schema.columns
--    WHERE table_name IN ('orders', 'sales')
--      AND column_name IN ('tax_amount', 'tax_rate');


-- REVIEW -------------------------------------------------------------------
--
-- Offline sales whose lines reached the server without the option or pack
-- they were sold as. Their stock moved against the item's own balance, one
-- unit per line. Nothing here changes them — this is the list to count in.
--
--   SELECT o.invoice_number,
--          s.sold_at,
--          i.name AS item,
--          oi.quantity,
--          oi.unit_factor
--     FROM orders o
--     JOIN sales s      ON s.order_id = o.id
--     JOIN order_items oi ON oi.order_id = o.id
--     JOIN items i        ON i.id = oi.item_id
--    WHERE o.invoice_number LIKE 'offline-%'
--      AND oi.variant_id IS NULL
--      AND oi.unit_id IS NULL
--    ORDER BY s.sold_at;
