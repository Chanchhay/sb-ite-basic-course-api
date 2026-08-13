-- 001 — Units become business-scoped, and existing ones get a symbol.
--
-- Background: a unit used to be global and admin-owned, so `slug` was unique
-- across the whole table. A unit now optionally belongs to a business
-- (`business_owner_id` null = platform unit), and two businesses are each
-- allowed to define their own "Sack". The old global unique index would refuse
-- the second one, and `ddl-auto: update` never drops an index it did not need
-- to add — so it has to go by hand.
--
-- Safe to run more than once.

BEGIN;

-- 0. The new columns, in case this runs before the app has booted on the new
-- code. Hibernate adds these itself under `ddl-auto: update`; doing it here as
-- well means the script does not depend on which happened first.
ALTER TABLE units ADD COLUMN IF NOT EXISTS business_owner_id uuid;
ALTER TABLE units ADD COLUMN IF NOT EXISTS symbol varchar(20);
ALTER TABLE units ADD COLUMN IF NOT EXISTS category varchar(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'units'::regclass
          AND contype = 'f'
          AND conname = 'fk_units_business_owner'
    ) THEN
        ALTER TABLE units
            ADD CONSTRAINT fk_units_business_owner
            FOREIGN KEY (business_owner_id) REFERENCES businesses (id);
    END IF;
END $$;

-- 1. Drop the global uniqueness on units.slug.
--
-- Hibernate generated the name, so it is looked up rather than assumed. It may
-- exist either as a table constraint or as a bare unique index, depending on
-- which version of the mapping created it; both are handled.
DO $$
DECLARE
    target_name text;
BEGIN
    FOR target_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'units'
          AND con.contype = 'u'
          AND (
              SELECT array_agg(att.attname ORDER BY att.attname)
              FROM unnest(con.conkey) AS k(attnum)
              JOIN pg_attribute att
                ON att.attrelid = con.conrelid AND att.attnum = k.attnum
          ) = ARRAY['slug']
    LOOP
        EXECUTE format('ALTER TABLE units DROP CONSTRAINT %I', target_name);
        RAISE NOTICE 'Dropped unique constraint % on units(slug)', target_name;
    END LOOP;

    FOR target_name IN
        SELECT idx.relname
        FROM pg_index i
        JOIN pg_class idx ON idx.oid = i.indexrelid
        JOIN pg_class rel ON rel.oid = i.indrelid
        WHERE rel.relname = 'units'
          AND i.indisunique
          AND NOT i.indisprimary
          -- Partial ones are the replacements added below, not the old index.
          AND i.indpred IS NULL
          AND (
              SELECT array_agg(att.attname ORDER BY att.attname)
              FROM unnest(i.indkey::smallint[]) AS k(attnum)
              JOIN pg_attribute att
                ON att.attrelid = i.indrelid AND att.attnum = k.attnum
          ) = ARRAY['slug']
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', target_name);
        RAISE NOTICE 'Dropped unique index % on units(slug)', target_name;
    END LOOP;
END $$;

-- 2. Uniqueness that still holds: one slug per business, and one slug among
-- the platform's own units. Two partial indexes rather than one constraint,
-- because Postgres treats NULLs as distinct and a plain unique index on
-- (business_owner_id, slug) would let duplicate platform slugs through.
CREATE UNIQUE INDEX IF NOT EXISTS uk_units_business_slug
    ON units (business_owner_id, slug)
    WHERE business_owner_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_units_platform_slug
    ON units (slug)
    WHERE business_owner_id IS NULL;

-- 3. Backfill the new columns on units that predate them.
--
-- Every existing unit stays a platform unit (business_owner_id left null),
-- which is what it was. The symbol falls back to the slug because there is
-- nothing better to derive it from — worth a pass by hand afterwards so
-- "kilogram" reads as "kg" rather than "kilogram".
UPDATE units
SET symbol = left(slug, 20)
WHERE symbol IS NULL OR symbol = '';

UPDATE units
SET category = 'COUNT'
WHERE category IS NULL;

COMMIT;

-- Check afterwards:
--
--   SELECT name, slug, symbol, category, business_owner_id FROM units ORDER BY name;
--   SELECT conname FROM pg_constraint WHERE conrelid = 'units'::regclass AND contype = 'u';
