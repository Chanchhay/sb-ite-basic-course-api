-- Channel pricing: what a channel charges instead, and when it is open.
--
-- Two tables, because they answer two different questions.
--
--   business_channel_settings — one row per shop per channel: the blanket rule
--   ("delivery is 10% dearer") and the weekly opening hours. Per business
--   rather than on sales_channels, which is shared: every shop has a counter,
--   but only this shop closes at nine.
--
--   item_channel_prices — one row per exception, on the same line Set Price
--   prices: the item on its own (variant and unit both null), one of its
--   options, or one of its larger units. A missing row means no exception,
--   which is why "reset to base" is a delete and not a rule of zero.
--
-- Whether an item is sold on a channel at all stays in item_channels, which
-- already existed and is untouched here.
--
-- Safe to run more than once.

BEGIN;

CREATE TABLE IF NOT EXISTS business_channel_settings (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses (id),
    sales_channel_id UUID NOT NULL REFERENCES sales_channels (id),
    override_kind VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    override_value NUMERIC(12, 4),
    schedule_json TEXT,
    created_date TIMESTAMP,
    last_modified_date TIMESTAMP,
    created_by VARCHAR(255),
    modified_by VARCHAR(255),
    CONSTRAINT uk_business_channel_settings UNIQUE (business_id, sales_channel_id)
);

CREATE TABLE IF NOT EXISTS item_channel_prices (
    id UUID PRIMARY KEY,
    sales_channel_id UUID NOT NULL REFERENCES sales_channels (id),
    item_id UUID NOT NULL REFERENCES items (id),
    variant_id UUID REFERENCES item_variants (id),
    unit_id UUID REFERENCES units (id),
    override_kind VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    override_value NUMERIC(12, 4),
    created_date TIMESTAMP,
    last_modified_date TIMESTAMP,
    created_by VARCHAR(255),
    modified_by VARCHAR(255)
);

-- One exception per line. Postgres treats NULLs as distinct in a plain unique
-- index, which would let the same item-on-its-own row be written twice, so the
-- nulls are folded to a sentinel first.
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_channel_price_line
    ON item_channel_prices (
        sales_channel_id,
        item_id,
        COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(unit_id, '00000000-0000-0000-0000-000000000000'::uuid)
    );

CREATE INDEX IF NOT EXISTS idx_item_channel_prices_channel_item
    ON item_channel_prices (sales_channel_id, item_id);

COMMIT;
