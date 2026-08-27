package kh.edu.istad.ite.features.dataimport.canonical;

import java.util.Map;

/**
 * A row once FluxiBiz has understood it, and before anything has judged it.
 *
 * The hinge of the whole feature. Upstream of this, code knows about files,
 * headings and cells; downstream, it knows only about names, SKUs and
 * quantities. That is what lets a new kind of source be added without
 * validation or commit noticing, and what stops a shop's column layout from
 * reaching into the catalogue.
 */
public sealed interface ImportRecord
        permits ItemGroupImportRecord, ItemImportRecord, OpeningStockImportRecord {

    /**
     * The record flattened for storage beside the raw row.
     *
     * What the checking step actually judged, kept so that a message about a
     * price can be shown next to the price we read rather than the text the
     * cell held.
     */
    Map<String, Object> normalized();

    /**
     * The row's identity in the system it came from, if it carried one.
     *
     * A SKU, a barcode, or failing both the name. What a second import of the
     * same file matches on.
     */
    String externalId();
}
