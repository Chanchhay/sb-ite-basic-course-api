package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;

/**
 * One row of an option group, still knowing which line of the file it was.
 *
 * The canonical record deliberately has no idea a file was involved, which is
 * what keeps validation and commit free of parsing concerns. But an option
 * group has to report back per row — this quantity went to that shelf — so the
 * line number travels beside the record rather than inside it.
 */
public record OptionRow(int rowNumber, ItemImportRecord record) {
}
