-- 011 — Let import jobs and rows hold the statuses an undo needs.
--
-- Undoing an import introduced two job statuses, REVERTING and REVERTED, and
-- one row status, REVERTED. Hibernate writes a CHECK constraint listing the
-- permitted values of a string enum column when it creates the table, and
-- `ddl-auto: update` never alters one afterwards — it adds columns and widens
-- them, and leaves every existing constraint exactly as it found it.
--
-- So on any database created before this feature, the very first act of an undo
-- — writing status = 'REVERTING' — is rejected by a constraint still listing
-- only the original eight. The application sees a DataIntegrityViolation, and
-- the shop sees a 409 on the undo it just asked for.
--
-- The constraints are dropped by lookup rather than by name. Hibernate's naming
-- is not something to rely on across versions, and a database that has been
-- through several of them may carry a name no longer generated. Anything
-- checking these two columns goes, and one constraint we have named ourselves
-- takes its place.
--
-- A future status needs another script like this one. That is the cost of the
-- constraint, and it is worth paying: it is what stops a typo in application
-- code writing a status nothing can read back.
--
-- Safe to run more than once.

BEGIN;

DO $$
DECLARE
    doomed record;
BEGIN
    FOR doomed IN
        SELECT rel.relname AS table_name, con.conname AS constraint_name
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
          JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
         WHERE con.contype = 'c'
           AND nsp.nspname = current_schema()
           AND rel.relname IN ('import_jobs', 'import_rows')
           AND pg_get_constraintdef(con.oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I',
                       doomed.table_name, doomed.constraint_name);
    END LOOP;
END $$;

ALTER TABLE import_jobs
    ADD CONSTRAINT ck_import_jobs_status CHECK (status IN (
        'UPLOADED', 'MAPPED', 'VALIDATING', 'READY', 'VALIDATION_FAILED',
        'COMMITTING', 'COMMITTED', 'REVERTING', 'REVERTED', 'FAILED'
    ));

ALTER TABLE import_rows
    ADD CONSTRAINT ck_import_rows_status CHECK (status IN (
        'PENDING', 'VALID', 'DUPLICATE', 'INVALID',
        'CREATED', 'UPDATED', 'SKIPPED', 'REVERTED', 'FAILED'
    ));

COMMIT;
