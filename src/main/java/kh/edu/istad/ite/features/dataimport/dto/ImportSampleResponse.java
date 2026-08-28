package kh.edu.istad.ite.features.dataimport.dto;

import java.util.List;

/**
 * One starting file a shop can download, described in their terms.
 *
 * Served rather than written into the screen so the words describing a sample
 * and the columns inside it cannot drift apart — a screen promising stock
 * columns on a file that has none sends someone looking for a column that was
 * never there.
 *
 * @param sample  what to ask for when downloading it
 * @param columns its headings, so the screen can show what is inside without
 *                anyone downloading it first
 */
public record ImportSampleResponse(
        String sample,
        String label,
        String description,
        String fileName,
        List<String> columns
) {
}
