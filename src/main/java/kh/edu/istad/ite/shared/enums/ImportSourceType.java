package kh.edu.istad.ite.shared.enums;

/**
 * Where the rows came from.
 *
 * Only uploaded files for now. It is an enum rather than a boolean because
 * everything downstream of ingestion — staging, validation, preview, commit —
 * works on rows and knows nothing about where they were read from, so a
 * database or an API source is a new value here and a new reader, not a
 * change to any of that.
 */
public enum ImportSourceType {
    CSV_UPLOAD,
    XLSX_UPLOAD
}
