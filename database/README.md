# Database scripts

There is no migration tool in this project — Hibernate runs with
`ddl-auto: update`, so it creates new tables and adds new columns on boot by
itself. What it will **never** do is drop or alter something that already
exists: a constraint that has become wrong, a column that changed meaning, data
that needs backfilling. That is what lives here.

**These run on boot. There is nothing to run by hand.** `SchemaScriptRunner`
picks up every `.sql` file in this directory — they are copied into the jar as
`db/migration/` by `processResources` — and applies them before the app serves
anything.

Some of the older scripts still open with "run after the app has booted once".
That wording predates the runner and is now wrong: those scripts run
themselves. It is left in place because editing an applied script changes its
checksum, which the runner reports as a warning on every subsequent boot — the
note is cheaper than the noise.

The scripts in `../src/main/resources/db/manual/` are a different thing and are
**deliberately** not run: they are hand-written equivalents of changes Hibernate
already makes under `ddl-auto: update`, kept for databases that do not run it —
and one of them, `cafe_mock_data.sql`, inserts mock data that must never reach a
real business.

**These run on boot.** `SchemaScriptRunner` applies them in number order, after
Hibernate has done its part, and records each one in a `schema_scripts` table so
it runs once. They used to be hand-run, which is exactly as reliable as somebody
remembering: `orders.tax_amount` sat missing for weeks because Hibernate could
not add it, logged the failure, and booted anyway. In a container it was worse
than forgetfulness — the image holds only the jar, so the scripts were not
present to run at all. They are packaged into it now (see `processResources` in
`build.gradle`), which is why they must stay in this directory.

A script that fails **stops the boot**. An app serving requests against a schema
it could not finish preparing is the failure this is here to prevent, so it is
loud rather than survivable. Set `app.database.auto-migrate: false` to skip the
whole step if you need to boot and repair by hand.

## Writing one

Number it after the last, and make it safe to run twice — that is what lets a
database where the scripts were already applied by hand adopt the runner
cleanly: its `schema_scripts` table starts empty, every script runs again, and
every one finds its work already done.

Never edit a script that has shipped. The runner notices (it stores a checksum),
warns, and does **not** re-run it, because re-running an edited script is how a
careful change becomes a destructive one. Add a new number instead.

To run one by hand anyway — against a database the app is not booting on, say:

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
- `stock_entries.lot_number`, `manufactured_at`, `expires_at` and
  `stock_layers.lot_number`, `manufactured_at`, `expires_at` — new nullable
  columns. A batch that predates them simply has no expiry, and queues behind
  every batch that has one. The **index** behind the new rotation order is not
  covered — see `006`.
- `business_audit_logs` — a new table: a shop's own record of who signed in and
  who changed staff or roles. Hibernate creates it with its indexes and the
  unique `(business_id, session_id)` constraint that keeps one sign-in from
  logging a row per request. No script and no backfill: nothing before it was
  recording sign-ins anywhere, so there is no history to recover. Distinct from
  `admin_audit_logs`, which is the platform's record of FluxiBiz staff acting
  *on* businesses and has no business column at all.
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
| `006_stock_batch_expiry.sql` | Indexes the expiry-first rotation order on `stock_layers`, and the lot number a recall is answered by. Hibernate adds the columns but knows nothing of the sort, and every sale reads that query. |
| `007_repair_sale_cost_unit_factor.sql` | Recomputes `sales.total_cost` for sales containing a pack line. It was summed as `unit_cost * quantity` where `unit_cost` is per *base* unit, so a case of 24 was costed as one unit and the margin was flattered. Only sales with a `unit_factor` other than 1 are touched. |
| `008_add_order_item_add_on_cost.sql` | Adds `order_item_add_ons.cost` with a default of zero, so an extra's FIFO cost lands on the line that charged for it. Hibernate cannot add a NOT NULL column to a table that already has rows. No backfill: pre-existing lines and the sales holding them both exclude add-on cost, so they agree. |
| `017_sales_register_session.sql` | Adds the index on the new `sales.register_session_id` and backfills it for sales that predate the column. A drawer's takings used to be reconstructed from cashier id and time window, which missed every cashier who *joined* a shared session and misfiled anything near a shift boundary; sales now carry the session they were rung up in. |
