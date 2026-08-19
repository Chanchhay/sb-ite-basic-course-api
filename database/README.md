# Database scripts

Hand-run SQL. There is no migration tool in this project — Hibernate runs with
`ddl-auto: update`, so it creates new tables and adds new columns on boot by
itself. What it will **never** do is drop or alter something that already
exists: a constraint that has become wrong, a column that changed meaning, data
that needs backfilling. That is what lives here.

Run the scripts in number order. Each one is written to be safe to run twice —
if it has already been applied, running it again does nothing.

```bash
psql "postgresql://$DB_USER@localhost:1681/fluxibix" -f database/001_units_scope_and_catalog_extras.sql
```

Port and database name come from `application-dev.yaml`; adjust if your
`PGHOST`/`PGPORT`/`PGDATABASE` differ.

## What Hibernate handles on its own

For reference, so nobody writes a script for something already covered. Booting
the app after these changes creates:

- `add_ons`, `item_add_ons`, `item_uom_conversions`, `stock_layers`,
  `stock_consumptions` — new tables
- `stock_entries.add_on_id`, `stock_layers.add_on_id` — new nullable columns
- `option_presets`, `add_on_uom_conversions`, `add_on_sets`,
  `add_on_set_items` — new tables
- `units.business_owner_id`, `units.symbol`, `units.category` — new nullable
  columns
- `item_variants.sku`, `item_variants.barcode`, `item_variants.image_url` — new
  nullable columns
- `stock_entries.cost_of_goods`, `unit_sale_price`, `entered_quantity`,
  `entered_unit_id` — new nullable columns
- `cart_item_selections`, `order_item_selections` — new tables, the options a
  line was ordered with ("Sugar Level: 50%"). Nothing to backfill: no order
  placed before them carried the choice anywhere, so there is nothing to
  recover.
- `items.colors` (jsonb), `item_variants.option_name`, `item_variants.color_value`
  — new nullable columns. An item declares its colours once (name, swatch, one
  photo) and each size ticks which it comes in; the saved row is the
  size-and-colour pair, so stock is kept per colour. `variant_name` stays the
  readable form ("Large / Red"), which is why carts, orders, receipts, POS and
  channel allocations needed no change. A variant with no `color_value` is a
  plain size and behaves exactly as before. `AttributeType.COLOR` and `AttributePlacement.HIDDEN` are no longer offered
  in the back office but remain in the enums, so any item already saved under
  one still loads. Migrate those by hand if you want the constants gone.
- `items.available_quantity` — **not** a column. Storefront availability is
  computed per request from stock less the channel's allocation; it is never
  stored.

## Scripts

| Script | What it does |
| --- | --- |
| `001_units_scope_and_catalog_extras.sql` | Drops the global unique index on `units.slug` so two businesses can both own a "Sack", and backfills `symbol`/`category` on existing units. |
| `002_stock_fifo_opening_layers.sql` | Opens one FIFO batch per item from its current balance, so stock that predates batch tracking can still be costed. Run after booting on the new code. |
| `003_stock_targets_allow_add_ons.sql` | Drops `NOT NULL` on `stock_entries.item_id` / `stock_layers.item_id` and adds a check that exactly one target is set, so add-ons can hold stock. Run after booting on the new code. |
| `005_add_tax_amount.sql` | Adds `orders.tax_amount` / `sales.tax_amount` with a default of zero. Postgres refuses a NOT NULL column with no default on a table that already has rows, so Hibernate cannot add these itself on any database with trading history. |
