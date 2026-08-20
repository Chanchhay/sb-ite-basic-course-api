-- 002 — Give existing stock a batch to be costed from.
--
-- Stock is now valued first-in-first-out: every arrival opens a layer at the
-- price it was bought for, and everything leaving consumes the oldest layers
-- and records what it took. Stock that was on the shelf before this change has
-- no layer behind it, so the first sale after the switch would find nothing to
-- consume and fall back to a cost of zero.
--
-- This opens one layer per item holding its current balance, priced at the last
-- cost the item was recorded with. It is an approximation by definition — the
-- real batch history was never kept — but it is the honest starting point, and
-- everything after it is exact.
--
-- Run once, after the app has booted on the new code (Hibernate creates
-- `stock_layers` and `stock_consumptions`). Safe to run again: items that
-- already have a layer are skipped.
--
-- Amended: it was not, quite. The skip compares `l.item_id = e.item_id`, and
-- once add-ons started recording stock with a null `item_id` that comparison
-- was null rather than true for them — so every run re-opened a layer against
-- no item at all, and once 003 had added its constraint the insert failed and
-- took the boot down with it. Only items are considered now.

BEGIN;

WITH latest_entry AS (
    -- The newest movement per item carries the running balance.
    --
    -- Items only. When this was written every movement had one; add-ons came
    -- later and hold stock in their own column, leaving `item_id` null. Those
    -- rows would otherwise collapse into a single null group and open one
    -- layer belonging to neither an item nor an add-on — which is exactly what
    -- 003 adds a constraint to forbid. Add-on stock is not this script's to
    -- open: it was never costed before add-ons existed.
    SELECT DISTINCT ON (item_id)
            item_id,
            business_owner_id,
            quantity_after,
            created_date
    FROM stock_entries
    WHERE item_id IS NOT NULL
    ORDER BY item_id, created_date DESC, id DESC
),
latest_cost AS (
    -- The newest movement that actually carried a cost.
    SELECT DISTINCT ON (item_id)
            item_id,
            unit_cost
    FROM stock_entries
    WHERE unit_cost IS NOT NULL
      AND item_id IS NOT NULL
    ORDER BY item_id, created_date DESC, id DESC
)
INSERT INTO stock_layers (
    id,
    business_owner_id,
    item_id,
    source_entry_id,
    unit_cost,
    quantity_received,
    quantity_remaining,
    received_at,
    created_date,
    last_modified_date
)
SELECT
    gen_random_uuid(),
    e.business_owner_id,
    e.item_id,
    NULL,
    COALESCE(c.unit_cost, 0),
    e.quantity_after,
    e.quantity_after,
    e.created_date,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM latest_entry e
LEFT JOIN latest_cost c ON c.item_id = e.item_id
WHERE e.quantity_after > 0
  AND NOT EXISTS (
      SELECT 1 FROM stock_layers l WHERE l.item_id = e.item_id
  );

COMMIT;

-- Check afterwards — on-hand per item should match the layer totals:
--
--   SELECT l.item_id,
--          sum(l.quantity_remaining) AS layered,
--          max(e.quantity_after)     AS on_hand
--   FROM stock_layers l
--   JOIN LATERAL (
--       SELECT quantity_after FROM stock_entries
--       WHERE item_id = l.item_id
--       ORDER BY created_date DESC, id DESC LIMIT 1
--   ) e ON true
--   GROUP BY l.item_id;
--
-- Items priced at 0 are ones that never had a cost recorded. Worth setting by
-- hand before they are sold:
--
--   SELECT item_id, unit_cost FROM stock_layers WHERE unit_cost = 0;
