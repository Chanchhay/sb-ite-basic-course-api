-- 012 — Move each existing migration's single file into the sources table.
--
-- An assisted migration used to hold exactly one of the customer's files, and
-- its details lived on the migration row itself: the name, the object key, the
-- headings, the column mapping. It can now hold several, so those details have
-- moved to `assisted_migration_sources`, one row per file.
--
-- Hibernate creates that table under `ddl-auto: update`, and creates it empty.
-- It has no way to know that the columns it is leaving behind on
-- `assisted_migrations` are the same file described a different way. So every
-- migration made before this change would come back with no files at all —
-- analysis, transform and handover all refusing with "this migration has no
-- files yet", against a job whose file is sitting in MinIO exactly where it
-- was left.
--
-- Hence this. One source row per migration that has a stored file, taking the
-- ordinal 0 that makes it the main one, and carrying its mapping across so an
-- operator resuming a half-finished job does not have to match the columns
-- again.
--
-- The old columns on `assisted_migrations` are deliberately left in place and
-- still written. The list and history screens read them to describe a job in
-- one line, and they now mirror whichever file is the main one — a smaller
-- change than teaching three screens to join.
--
-- Safe to run more than once: it inserts only where no source exists yet.

BEGIN;

INSERT INTO assisted_migration_sources (
    id,
    migration_id,
    ordinal,
    purpose,
    source_type,
    file_name,
    file_size,
    raw_object_key,
    sheet_name,
    row_count,
    column_count,
    source_columns,
    column_mappings,
    analyzed,
    created_date,
    last_modified_date,
    created_by,
    modified_by
)
SELECT
    gen_random_uuid(),
    m.id,
    0,
    -- The main file of a migration that has one file is its catalogue. A
    -- migration made before this change could not have held anything else.
    'PRODUCTS',
    m.source_type,
    m.source_file_name,
    m.source_file_size,
    m.raw_object_key,
    m.source_sheet,
    m.row_count,
    m.column_count,
    COALESCE(m.source_columns, '[]'::jsonb),
    COALESCE(m.column_mappings, '{}'::jsonb),
    -- Anything with a row count has been read, so an operator returning to it
    -- lands where they left rather than at the start.
    (m.row_count IS NOT NULL),
    m.created_date,
    m.last_modified_date,
    m.created_by,
    m.modified_by
  FROM assisted_migrations m
 WHERE m.raw_object_key IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
         FROM assisted_migration_sources s
        WHERE s.migration_id = m.id
   );

COMMIT;
