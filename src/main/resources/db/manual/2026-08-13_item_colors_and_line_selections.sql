-- Colours as a thing the item declares, and the options a line was ordered with.
--
-- Two unrelated changes, in one script because they ship together.
--
--   items.colors — the colours an item comes in, declared once for the whole
--   item rather than per size: the same red shirt photographed for Small is
--   the same photograph for Large. Null on every item that predates this,
--   which reads as "not sold by colour" — the mapper answers an empty list for
--   a null, so nothing needs backfilling.
--
--   item_variants.option_name, .color_value — the two halves of what a variant
--   is. `variant_name` stays the readable form of the pair ("Large / Red"), so
--   carts, orders, receipts, POS and channel allocations keep reading the one
--   field they always read. A variant with no color_value is a plain size and
--   behaves exactly as it did.
--
--   cart_item_selections, order_item_selections — the options a line was
--   ordered with ("Sugar Level: 50%"). A selection is neither a variant nor an
--   add-on: it changes nothing about what is charged or what comes off the
--   shelf, and only says how the thing should be made. Nothing to backfill —
--   no order placed before these tables carried the choice anywhere, so there
--   is nothing to recover.
--
-- The name and value are stored as text rather than pointed at the item's
-- attribute, for the same reason the add-on beside them copies its name and
-- price: a shop that renames "Sugar Level" to "Sweetness" next month must not
-- change what last month's ticket said.
--
-- Not here, because it needs no DDL: option preset values gained an `imageUrl`,
-- and those live inside the `option_presets.values` jsonb. Rows written before
-- it simply have no such key, which deserialises to null.
--
-- Dev runs with ddl-auto: update and picks all of this up on its own. This is
-- for anywhere that does not. Safe to run more than once.

BEGIN;

ALTER TABLE items ADD COLUMN IF NOT EXISTS colors JSONB;

ALTER TABLE item_variants ADD COLUMN IF NOT EXISTS option_name VARCHAR(150);
ALTER TABLE item_variants ADD COLUMN IF NOT EXISTS color_value VARCHAR(150);

-- `value` is the stored identity and `label` is how it was shown; label is
-- nullable and falls back to value, so a line that was never relabelled costs
-- nothing to keep.
CREATE TABLE IF NOT EXISTS cart_item_selections (
    id UUID PRIMARY KEY,
    cart_item_id UUID NOT NULL,
    attribute_name VARCHAR(150) NOT NULL,
    value VARCHAR(150) NOT NULL,
    label VARCHAR(150),
    CONSTRAINT fk_cart_item_selections_line
        FOREIGN KEY (cart_item_id) REFERENCES cart_items (id)
);

CREATE TABLE IF NOT EXISTS order_item_selections (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL,
    attribute_name VARCHAR(150) NOT NULL,
    value VARCHAR(150) NOT NULL,
    label VARCHAR(150),
    CONSTRAINT fk_order_item_selections_line
        FOREIGN KEY (order_item_id) REFERENCES order_items (id)
);

-- Every read of a selection is "the options on this line", so the line is what
-- to index. Hibernate creates neither of these on its own.
CREATE INDEX IF NOT EXISTS idx_cart_item_selections_line
    ON cart_item_selections (cart_item_id);

CREATE INDEX IF NOT EXISTS idx_order_item_selections_line
    ON order_item_selections (order_item_id);

COMMIT;
