-- The `sales.payment_method` check constraint was created directly against
-- the database (not by Hibernate — it never generates CHECK constraints
-- for an @Enumerated(EnumType.STRING) column, so this predates this repo's
-- migration files) back when PaymentMethodType only had CASH and DIGITAL.
-- PAY_LATER was added to the Java enum afterward, but this constraint was
-- never widened to match, so every Pay Later sale insert has been failing
-- with "violates check constraint sales_payment_method_check" ever since —
-- unrelated to any application code, purely a stale constraint.
--
-- Run this once, directly against the database (ddl-auto: update will
-- never touch it, since Hibernate doesn't know this constraint exists).

ALTER TABLE sales DROP CONSTRAINT IF EXISTS sales_payment_method_check;

ALTER TABLE sales ADD CONSTRAINT sales_payment_method_check
    CHECK (payment_method IN ('CASH', 'DIGITAL', 'PAY_LATER'));

-- Same drift risk on `channel` columns — OrderChannel is POS/TELEGRAM/
-- MESSENGER/WEB in the Java enum; widen both tables' constraints (if any
-- exist under these names) to match, so a TELEGRAM-tagged order/sale
-- can never hit this same class of bug.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_channel_check;
ALTER TABLE orders ADD CONSTRAINT orders_channel_check
    CHECK (channel IN ('POS', 'TELEGRAM', 'MESSENGER', 'WEB'));

ALTER TABLE sales DROP CONSTRAINT IF EXISTS sales_channel_check;
ALTER TABLE sales ADD CONSTRAINT sales_channel_check
    CHECK (channel IN ('POS', 'TELEGRAM', 'MESSENGER', 'WEB'));
