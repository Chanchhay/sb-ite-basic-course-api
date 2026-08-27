-- Business location: geocoded text + map coordinates, no seeded division table.
--
-- Earlier draft of this migration created an administrative_divisions
-- reference table (province/district/commune/village) that the business
-- dashboard would seed and cascade-select against. Dropped in favor of this:
-- province_name/district_name/commune_name are plain text, filled in from a
-- geocoder's address components when the owner drops the map pin — nobody on
-- this team has the bandwidth to hand-seed Cambodia's ~1,600 communes, and a
-- geocoder already normalizes spelling/casing better than free entry did.
--
-- Safe to run more than once.

BEGIN;

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS province_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS district_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS commune_name  VARCHAR(150),
    ADD COLUMN IF NOT EXISTS latitude      NUMERIC(9,6),
    ADD COLUMN IF NOT EXISTS longitude     NUMERIC(9,6);

CREATE INDEX IF NOT EXISTS idx_businesses_province_name ON businesses (province_name);

COMMIT;
