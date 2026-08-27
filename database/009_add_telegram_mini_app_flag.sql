-- 009 — Add `is_mini_app_enabled` to business_telegram_bots.
--
-- The Telegram mini app work added `BusinessTelegramBot.isMiniAppEnabled` as
-- NOT NULL. On a fresh database Hibernate creates the column with the table and
-- there is nothing to do; on a database that already has a bot configured there
-- is, because Postgres will not add a NOT NULL column with no default to a
-- table that already has rows:
--
--   ERROR: column "is_mini_app_enabled" of relation "business_telegram_bots"
--          contains null values
--
-- Hibernate logs that and carries on booting, so the application comes up
-- without the column and every read or write of a shop's bot settings then
-- fails. Exactly the shape of 005, and for the same reason.
--
-- False is the right value for every row that predates the feature: a bot
-- configured before there was a mini app plainly did not have one enabled. The
-- entity carries the same default, so no existing shop sees a change in
-- behaviour.
--
-- The default stays on the column rather than being dropped afterwards. The
-- entity always supplies the value, so nothing depends on it — but it is a
-- NOT NULL flag, and defaulting to "off" is a better failure than a constraint
-- violation if anything ever inserts without one.
--
-- Run before booting on the new code, or after the boot that logged the error;
-- either way the application needs a restart to pick the column up. Safe to run
-- more than once.

BEGIN;

ALTER TABLE business_telegram_bots
    ADD COLUMN IF NOT EXISTS is_mini_app_enabled boolean NOT NULL DEFAULT false;

COMMIT;
