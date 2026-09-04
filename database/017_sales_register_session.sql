-- 017 — Give every sale the drawer its money went into.
--
-- A register session's takings used to be worked out backwards: sum the cash
-- sales whose cashier_id matched the session's user_id, between the session's
-- opened_at and closed_at. Both halves of that guess were wrong.
--
-- The cashier half missed everyone who joined the drawer. A session is shared
-- — one cashier opens it, the rest of the shift joins in — but only the opener
-- was ever matched, so a joining cashier's cash was invisible to the till they
-- had physically put it in, and closing reported SHORT by exactly what they
-- had rung up.
--
-- The time half misfiles anything near a boundary. A sale rung up as one shift
-- hands over to the next lands in whichever drawer the clock picks, not the
-- one holding the notes.
--
-- Sales now carry `register_session_id`, stamped when the sale is settled
-- (OrderServiceImpl.registerSessionIdFor) — the one moment the answer is
-- actually known rather than inferred. This adds the index the register
-- history reads it through, and applies the old rule one last time to the
-- rows that predate the column.
--
-- Hibernate adds the column itself: it is nullable, and null is meaningful —
-- an online order takes no cash at a till and belongs to no drawer.
--
-- Safe to run more than once.

BEGIN;

-- Hibernate adds this on boot, before the runner gets here. Stated anyway so
-- the script stands on its own against a database that does not run ddl-auto.
ALTER TABLE sales ADD COLUMN IF NOT EXISTS register_session_id bigint;

-- Every read of a session's takings filters on this, and closing a till waits
-- on one.
CREATE INDEX IF NOT EXISTS idx_sales_register_session
    ON sales (register_session_id);

-- Backfill: the old cashier-and-window rule, applied once, to rows that have
-- no stamp of their own.
--
-- Widened to the drawer's full roster — opener unioned with participants — so
-- shifts that were under-reported at the time are repaired rather than carried
-- forward wrong. Sessions predating the shared drawer have no participant rows
-- and fall back to the opener alone, which is all they ever had.
--
-- DISTINCT ON keeps this deterministic if two sessions ever overlapped a sale:
-- the earlier drawer wins, and no sale is counted twice.
WITH matched AS (
    SELECT DISTINCT ON (sa.id) sa.id AS sale_id, rs.id AS session_id
    FROM sales sa
    JOIN register_sessions rs
      ON rs.business_id = sa.business_owner_id
     AND sa.cashier_id::text IN (
             SELECT rs.user_id
             UNION
             SELECT p.user_id
             FROM register_session_participants p
             WHERE p.session_id = rs.id
         )
     -- sales.sold_at is a wall-clock timestamp; register_sessions.opened_at is
     -- an instant. Compared raw they are out by the offset, which at the far
     -- end of a shift is the difference between the right drawer and the next
     -- one. Converted the same way the query this replaces did.
     AND sa.sold_at >= (rs.opened_at AT TIME ZONE current_setting('TimeZone'))
     AND sa.sold_at <= COALESCE(
             rs.closed_at AT TIME ZONE current_setting('TimeZone'),
             now() AT TIME ZONE current_setting('TimeZone'))
    WHERE sa.register_session_id IS NULL
      AND sa.cashier_id IS NOT NULL
    ORDER BY sa.id, rs.opened_at
)
UPDATE sales sa
SET register_session_id = matched.session_id
FROM matched
WHERE sa.id = matched.sale_id;

COMMIT;
