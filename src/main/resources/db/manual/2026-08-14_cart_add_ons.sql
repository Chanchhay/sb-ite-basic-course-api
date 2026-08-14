-- Add-ons in the basket: the extras a shopper ticks on the online store.
--
-- The till could already sell them (order_item_add_ons), but the web goes
-- through a basket, and a basket had nowhere to keep them — so a shopper's
-- extras were dropped between the product page and the order. This is the
-- same row, one step earlier, snapshotted for the same reason: a basket left
-- open overnight must still cost what it said it cost.
--
-- Dev runs with ddl-auto: update and picks this up on its own. Safe to run
-- against a database that already has it.

CREATE TABLE IF NOT EXISTS cart_item_add_ons (
    id            UUID PRIMARY KEY,
    cart_item_id  UUID           NOT NULL,
    add_on_id     UUID,
    add_on_name   VARCHAR(150)   NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    use_per_order NUMERIC(12, 3) NOT NULL DEFAULT 1,
    CONSTRAINT fk_cart_item_add_ons_line
        FOREIGN KEY (cart_item_id) REFERENCES cart_items (id),
    CONSTRAINT fk_cart_item_add_ons_add_on
        FOREIGN KEY (add_on_id) REFERENCES add_ons (id)
);

CREATE INDEX IF NOT EXISTS idx_cart_item_add_ons_line
    ON cart_item_add_ons (cart_item_id);
