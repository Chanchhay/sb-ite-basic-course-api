-- A larger unit belongs to one option.
--
-- A shop that sells Large by the case need not sell Small that way, and the
-- two need not even hold the same number — so the option is part of what a
-- conversion is. Null means the item is sold as itself, with no options.
--
-- The old uniqueness was one row per (item, unit); it becomes one row per
-- (item, unit, option). Hibernate never drops an index it did not need to add,
-- so the old one goes by hand.
--
-- Safe to run more than once.

BEGIN;

ALTER TABLE item_uom_conversions
    ADD COLUMN IF NOT EXISTS variant_id UUID;

ALTER TABLE item_uom_conversions
    DROP CONSTRAINT IF EXISTS uk_item_uom_conversions_item_unit;

ALTER TABLE item_uom_conversions
    DROP CONSTRAINT IF EXISTS fk_item_uom_conversions_variant;

ALTER TABLE item_uom_conversions
    ADD CONSTRAINT fk_item_uom_conversions_variant
    FOREIGN KEY (variant_id) REFERENCES item_variants (id);

-- Postgres treats NULLs as distinct in a unique index, so an item with no
-- options still gets one row per unit.
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_uom_conversions_item_unit_variant
    ON item_uom_conversions (item_id, unit_id, variant_id);

COMMIT;
