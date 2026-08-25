-- 005 — Add `tax_amount` to orders and sales.
--
-- The tax work added `Order.taxAmount` and `Sale.taxAmount` as NOT NULL. On a
-- fresh database Hibernate creates the column with the table and there is
-- nothing to do; on a database that already has trading history there is,
-- because Postgres will not add a NOT NULL column with no default to a table
-- that already has rows:
--
--   ERROR: column "tax_amount" of relation "orders" contains null values
--
-- Hibernate logs that and carries on, so the app boots without the column and
-- every read of an order or a sale then fails. This adds it with a default of
-- zero, which is the right value for every row that predates the feature: an
-- order taken before tax was recorded had no tax on it.
--
-- The default stays on the column rather than being dropped afterwards. The
-- entity always supplies the value, so nothing depends on it — but it is a
-- NOT NULL money column, and a default of zero is a better failure than a
-- constraint violation if anything ever inserts without one.
--
-- Run before booting on the new code, or after the boot that logged the error;
-- either way the app needs a restart to pick the column up. Safe to run more
-- than once.

BEGIN;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE sales  ADD COLUMN IF NOT EXISTS tax_amount numeric(12,2) NOT NULL DEFAULT 0;

COMMIT;
