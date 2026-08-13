-- Per-option stock: an item sold in options counts each one separately.
--
-- Dev runs with ddl-auto: update and picks these columns up on its own. Any
-- database that does not is migrated with this, and it is safe to run against
-- one that already has them.
--
-- Nothing is backfilled. Every existing row keeps variant_id NULL, which reads
-- as "held against the item as a whole" — the balance a shop had before its
-- items gained options. Those quantities stay where they are and stay
-- correctable; guessing which option they belonged to would invent a count
-- nobody recorded.

ALTER TABLE stock_entries
    ADD COLUMN IF NOT EXISTS variant_id UUID;

ALTER TABLE stock_entries
    DROP CONSTRAINT IF EXISTS fk_stock_entries_variant;

ALTER TABLE stock_entries
    ADD CONSTRAINT fk_stock_entries_variant
    FOREIGN KEY (variant_id) REFERENCES item_variants (id);

ALTER TABLE stock_layers
    ADD COLUMN IF NOT EXISTS variant_id UUID;

ALTER TABLE stock_layers
    DROP CONSTRAINT IF EXISTS fk_stock_layers_variant;

ALTER TABLE stock_layers
    ADD CONSTRAINT fk_stock_layers_variant
    FOREIGN KEY (variant_id) REFERENCES item_variants (id);

-- Every balance is read by walking one target's chain newest-first, and every
-- sale walks its option's open batches oldest-first. Both are per option now.
CREATE INDEX IF NOT EXISTS idx_stock_entries_target
    ON stock_entries (business_owner_id, item_id, variant_id, created_date DESC);

CREATE INDEX IF NOT EXISTS idx_stock_layers_open
    ON stock_layers (business_owner_id, item_id, variant_id, received_at);
