-- 016 — Point unfinished orders at the currency their amounts are actually in.
--
-- An order freezes its currency when it is created. Moving the base currency
-- used to restate the catalogue, the discounts, the stock costs and the
-- storefront carts, but not the POS orders still being rung up — so an order
-- opened before a switch kept naming the old code while every line added
-- afterwards was priced in the new base.
--
-- The till then labelled new-base amounts with the old symbol, and, because
-- the old code is still a configured currency, the secondary line converted
-- figures that were already in the base a second time: a 410 total showing
-- as "$410.00" with "1,681,016.81" underneath it.
--
-- The service now restates open orders as part of the base change
-- (BusinessCurrencyServiceImpl.repriceOpenOrders). This repairs the rows that
-- predate that fix.
--
-- Only PENDING orders are touched, and only their currency label — the
-- amounts on them are already the new base's numbers, which is the whole
-- problem. A CONFIRMED or PAID order is a record of what a customer was
-- actually charged and is left exactly as it is, receipts included.
--
-- Safe to run more than once.

BEGIN;

-- The stale display pair is quoted against the old currency, so it would
-- misconvert against the corrected one. Cleared; the next push re-snapshots
-- it from the business's live configuration.
UPDATE orders o
SET currency = b.base_currency,
    display_currency = NULL,
    display_exchange_rate = NULL
FROM businesses b
WHERE o.business_owner_id = b.id
  AND o.status = 'PENDING'
  AND b.base_currency IS NOT NULL
  AND o.currency IS DISTINCT FROM b.base_currency;

COMMIT;
