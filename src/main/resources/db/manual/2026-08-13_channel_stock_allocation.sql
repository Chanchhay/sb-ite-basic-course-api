-- Channel stock allocation: how much of one shelf each channel may sell.
--
-- There is still one shelf. An item has one balance per option and every sale
-- comes off it — nothing here holds stock of its own. What it holds is a
-- ceiling per channel, and how much of that ceiling has been used.
--
--   items.channel_stock_mode — SHARED or ALLOCATED, per item. Nullable, and
--   null reads as SHARED: an item that predates this was never asked the
--   question, and must go on selling from the whole shelf everywhere.
--
--   item_channel_stocks — one row per channel per option: `quantity` is what
--   the shop allowed, `sold_quantity` is what that channel has since sold. A
--   missing row is not a ceiling of zero under SHARED — it only stops a
--   channel selling once the item is ALLOCATED.
--
-- Dev runs with ddl-auto: update and picks both up on its own. This is for
-- anywhere that does not. Safe to run more than once.

BEGIN;

ALTER TABLE items ADD COLUMN IF NOT EXISTS channel_stock_mode VARCHAR(20);

CREATE TABLE IF NOT EXISTS item_channel_stocks (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES items (id),
    sales_channel_id UUID NOT NULL REFERENCES sales_channels (id),
    variant_id UUID REFERENCES item_variants (id),
    quantity NUMERIC(18, 3) NOT NULL DEFAULT 0,
    sold_quantity NUMERIC(18, 3) NOT NULL DEFAULT 0,
    created_date TIMESTAMP,
    last_modified_date TIMESTAMP,
    created_by VARCHAR(255),
    modified_by VARCHAR(255)
);

-- One allocation per channel per option. The option is part of the key
-- because stock is: ten Smalls says nothing about the Larges.
--
-- A plain UNIQUE would not hold it — variant_id is null for an item with no
-- options, and null is never equal to null, so two rows for the same channel
-- would both be allowed. Hence two partial indexes.
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_channel_stock_variant
    ON item_channel_stocks (item_id, sales_channel_id, variant_id)
    WHERE variant_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_item_channel_stock_item
    ON item_channel_stocks (item_id, sales_channel_id)
    WHERE variant_id IS NULL;

COMMIT;
