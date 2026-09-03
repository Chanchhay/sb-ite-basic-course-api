-- 015 — Widen `businesses.about` from varchar(255) to varchar(300).
--
-- The business profile's public description was raised from 255 to 300
-- characters, in step with the item description. Three places had to agree:
-- the form's Zod schema and textarea, `UpdateBusinessRequest.about`'s
-- @Size, and `Business.about`'s @Column(length) — all now 300.
--
-- Hibernate's ddl-auto does not widen an existing column. On a fresh database
-- it creates varchar(300) from the entity and there is nothing to do; on a
-- database that already has the table, the column stays varchar(255) and the
-- first merchant to save a 256+ character description gets:
--
--   ERROR: value too long for type character varying(255)
--
-- The backend would have accepted the request by then — its @Size now says
-- 300 — so the failure surfaces as a 500 on save rather than a field error,
-- which is exactly the dead end the validation pass was meant to remove.
--
-- Widening a varchar is a metadata-only change in Postgres: no table rewrite,
-- no lock beyond a brief ACCESS EXCLUSIVE, and no risk to existing rows, since
-- every value that fitted in 255 fits in 300. Nothing to backfill.
--
-- SchemaScriptRunner applies this on boot, after Hibernate and in number
-- order, and records it so it runs once.

BEGIN;

ALTER TABLE businesses
    ALTER COLUMN about TYPE varchar(300);

COMMIT;
