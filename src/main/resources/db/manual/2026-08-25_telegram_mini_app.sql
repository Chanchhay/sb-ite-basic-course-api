-- Telegram Mini App toggle: whether a business's bot menu button opens the
-- Mini App (a real web UI) instead of Telegram's default commands list.
--
-- Safe to run more than once.

BEGIN;

ALTER TABLE business_telegram_bots
    ADD COLUMN IF NOT EXISTS is_mini_app_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMIT;
