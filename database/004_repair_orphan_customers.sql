-- 004 — Repair the rows pointing at a customer that no longer exists.
--
-- One customer was removed without its references going with it, and four
-- tables still name it. Every one of those references is unusable: the row
-- points at a row that cannot be loaded, so nothing in the app can resolve it.
-- This is also why Hibernate has never been able to create the four foreign
-- keys it tries to add on every boot — Postgres validates existing rows, finds
-- these, and refuses.
--
-- The repair differs per table, because what the reference is worth differs:
--
--   orders   — the sale is real and the app reads it. Only the customer link
--              is broken, so it is cleared and the order kept. Deleting these
--              would throw away revenue history to fix a null.
--   carts    — `customer_id` is NOT NULL, and a basket belonging to nobody can
--              never be reopened. Removed, with its lines.
--   bot_sessions, customer_channel_identities — a conversation with a customer
--              that no longer exists. Cleared.
--
-- Run after restoring onto the new server, before adding the foreign keys at
-- the bottom. Safe to run more than once.

BEGIN;

-- Orders: keep the sale, drop the dangling link.
UPDATE orders
SET customer_id = NULL
WHERE customer_id IS NOT NULL
  AND customer_id NOT IN (SELECT id FROM customers);

-- Baskets: NOT NULL, and unreachable. Lines first.
DELETE FROM cart_item_selections WHERE cart_item_id IN (
    SELECT ci.id FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
    WHERE c.customer_id NOT IN (SELECT id FROM customers));

DELETE FROM cart_item_add_ons WHERE cart_item_id IN (
    SELECT ci.id FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
    WHERE c.customer_id NOT IN (SELECT id FROM customers));

DELETE FROM cart_items WHERE cart_id IN (
    SELECT id FROM carts WHERE customer_id NOT IN (SELECT id FROM customers));

DELETE FROM carts WHERE customer_id NOT IN (SELECT id FROM customers);

-- Bot conversations: nullable, so the session survives without the link.
UPDATE bot_sessions
SET customer_id = NULL
WHERE customer_id IS NOT NULL
  AND customer_id NOT IN (SELECT id FROM customers);

DELETE FROM customer_channel_identities
WHERE customer_id IS NOT NULL
  AND customer_id NOT IN (SELECT id FROM customers);

COMMIT;

-- The four constraints that could never be created while those rows existed.
-- Named as Hibernate names them, so it recognises them and stops retrying.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkpxtb8awmi0dk6smoh2vp1litg') THEN
        ALTER TABLE orders ADD CONSTRAINT fkpxtb8awmi0dk6smoh2vp1litg
            FOREIGN KEY (customer_id) REFERENCES customers (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk8ba3sryid5k8a9kidpkvqipyt') THEN
        ALTER TABLE carts ADD CONSTRAINT fk8ba3sryid5k8a9kidpkvqipyt
            FOREIGN KEY (customer_id) REFERENCES customers (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkojm0jhhrky63bsx7vf59ha7rd') THEN
        ALTER TABLE bot_sessions ADD CONSTRAINT fkojm0jhhrky63bsx7vf59ha7rd
            FOREIGN KEY (customer_id) REFERENCES customers (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk107p31licxrcj85qn4ntle3d') THEN
        ALTER TABLE customer_channel_identities ADD CONSTRAINT fk107p31licxrcj85qn4ntle3d
            FOREIGN KEY (customer_id) REFERENCES customers (id);
    END IF;
END $$;

-- Check afterwards — all four should report 0:
--
--   SELECT count(*) FROM orders WHERE customer_id NOT IN (SELECT id FROM customers);
