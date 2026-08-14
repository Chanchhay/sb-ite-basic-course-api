-- Selling by the pack: a case, a six-pack, a pallet.
--
-- Stock is still held in base units. A conversion gains a price of its own —
-- a case is not twenty-four times a can, or nobody would buy the case — and a
-- sold line remembers which unit it was sold in and how many base units that
-- held at the time.
--
-- The factor is snapshotted on the line rather than read back from the item: a
-- shop that redefines its case from 24 to 12 must not change what last month's
-- receipt meant, or what it took off the shelf.
--
-- Dev runs with ddl-auto: update and picks these up on its own. Safe to run
-- against a database that already has them.

ALTER TABLE item_uom_conversions
    ADD COLUMN IF NOT EXISTS price NUMERIC(12, 2);

-- Existing lines were all sold in the base unit, which is a factor of one.
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS unit_id UUID;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS unit_factor NUMERIC(18, 6) DEFAULT 1;

UPDATE order_items SET unit_factor = 1 WHERE unit_factor IS NULL;

ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS fk_order_items_unit;

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_unit
    FOREIGN KEY (unit_id) REFERENCES units (id);

ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS unit_id UUID;

ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS unit_factor NUMERIC(18, 6) DEFAULT 1;

UPDATE cart_items SET unit_factor = 1 WHERE unit_factor IS NULL;

ALTER TABLE cart_items
    DROP CONSTRAINT IF EXISTS fk_cart_items_unit;

ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_items_unit
    FOREIGN KEY (unit_id) REFERENCES units (id);
