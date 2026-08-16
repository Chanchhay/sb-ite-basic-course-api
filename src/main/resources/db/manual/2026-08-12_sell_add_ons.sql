-- Selling add-ons: what an extra costs, whether an item still offers it, and
-- which extras went out on a line.
--
-- The price is one number for the whole business; whether it is on the menu is
-- per item, because a shop that runs out of pearls takes them off one drink
-- without unlinking them from it.
--
-- Dev runs with ddl-auto: update and picks these up on its own. Safe to run
-- against a database that already has them.

ALTER TABLE add_ons
    ADD COLUMN IF NOT EXISTS price NUMERIC(12, 2);

-- Existing links predate the column and are all on sale.
ALTER TABLE item_add_ons
    ADD COLUMN IF NOT EXISTS available BOOLEAN DEFAULT TRUE;

UPDATE item_add_ons SET available = TRUE WHERE available IS NULL;

ALTER TABLE item_add_ons
    ALTER COLUMN available SET NOT NULL;

CREATE TABLE IF NOT EXISTS order_item_add_ons (
    id            UUID PRIMARY KEY,
    order_item_id UUID           NOT NULL,
    add_on_id     UUID,
    add_on_name   VARCHAR(150)   NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    use_per_order NUMERIC(12, 3) NOT NULL DEFAULT 1,
    CONSTRAINT fk_order_item_add_ons_line
        FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_order_item_add_ons_add_on
        FOREIGN KEY (add_on_id) REFERENCES add_ons (id)
);

CREATE INDEX IF NOT EXISTS idx_order_item_add_ons_line
    ON order_item_add_ons (order_item_id);
