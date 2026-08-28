-- Hibernate's `ddl-auto: update` cannot add a NOT NULL column to a table
-- that already has rows without an explicit DEFAULT — it silently fails at
-- startup (logged as a warning, easy to miss), leaving the JPA entity
-- mapping (which already expects this column) out of sync with the real
-- schema. Every query against BusinessFacebookPage then fails with
-- "column bfp1_0.is_mini_app_enabled does not exist".
--
-- Run this once, directly against the database.

ALTER TABLE business_facebook_pages
    ADD COLUMN IF NOT EXISTS is_mini_app_enabled boolean NOT NULL DEFAULT false;
