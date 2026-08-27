package kh.edu.istad.ite.features.dataimport.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.ItemType;

class RowReaderTest {

    private RowReader readerFor(ImportField field, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("col", value);

        Map<ImportField, String> byField = new EnumMap<>(ImportField.class);
        byField.put(field, "col");

        MappingPlan plan = new MappingPlan(
                ImportTargetType.ITEM, byField, ImportDuplicateStrategy.SKIP, null, null);

        return new RowReader(new SourceRow(2, values), plan);
    }

    private BigDecimal number(String raw) {
        return readerFor(ImportField.PRICE, raw).number(ImportField.PRICE);
    }

    @Test
    void readsAPlainNumber() {
        assertThat(number("2.50")).isEqualByComparingTo("2.50");
    }

    /** Old systems export prices with the currency stuck to them. */
    @Test
    void ignoresCurrencySymbolsAndSpaces() {
        assertThat(number("$ 2.50")).isEqualByComparingTo("2.50");
        assertThat(number("2.50 USD")).isEqualByComparingTo("2.50");
    }

    @Test
    void readsThousandsSeparators() {
        assertThat(number("1,250.75")).isEqualByComparingTo("1250.75");
    }

    /** Where both separators appear, the last one is the decimal point. */
    @Test
    void readsEuropeanStyleNumbers() {
        assertThat(number("1.250,75")).isEqualByComparingTo("1250.75");
    }

    /**
     * A lone comma with two digits after it is a decimal point; with three it
     * is a thousands separator. Guessing the other way turns 1,250 into 1.25.
     */
    @Test
    void tellsADecimalCommaFromAThousandsComma() {
        assertThat(number("2,50")).isEqualByComparingTo("2.50");
        assertThat(number("1,250")).isEqualByComparingTo("1250");
    }

    @Test
    void reportsSomethingThatIsNotANumber() {
        RowReader reader = readerFor(ImportField.PRICE, "call us");

        assertThat(reader.number(ImportField.PRICE)).isNull();
        assertThat(reader.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.code()).isEqualTo("NOT_A_NUMBER");
                    assertThat(issue.isError()).isTrue();
                });
    }

    @Test
    void readsAnEmptyCellAsNothingRatherThanZero() {
        assertThat(number("")).isNull();
        assertThat(number(null)).isNull();
    }

    @Test
    void readsYesAndNoInTheFormsExportsUse() {
        assertThat(readerFor(ImportField.TRACK_INVENTORY, "Yes").flag(ImportField.TRACK_INVENTORY)).isTrue();
        assertThat(readerFor(ImportField.TRACK_INVENTORY, "1").flag(ImportField.TRACK_INVENTORY)).isTrue();
        assertThat(readerFor(ImportField.TRACK_INVENTORY, "FALSE").flag(ImportField.TRACK_INVENTORY)).isFalse();
        assertThat(readerFor(ImportField.TRACK_INVENTORY, "no").flag(ImportField.TRACK_INVENTORY)).isFalse();
    }

    @Test
    void readsItemTypesUnderTheirCommonNames() {
        assertThat(readerFor(ImportField.ITEM_TYPE, "PHYSICAL").itemType(ImportField.ITEM_TYPE))
                .isEqualTo(ItemType.PHYSICAL);
        assertThat(readerFor(ImportField.ITEM_TYPE, "goods").itemType(ImportField.ITEM_TYPE))
                .isEqualTo(ItemType.PHYSICAL);
        assertThat(readerFor(ImportField.ITEM_TYPE, "Service").itemType(ImportField.ITEM_TYPE))
                .isEqualTo(ItemType.SERVICE);
    }

    /**
     * Every old system has its own word for this. "Standard" must not cost a
     * shop their whole catalogue — it warns, and the mapper falls back.
     */
    @Test
    void warnsRatherThanFailsOnAnItemTypeItDoesNotRecognise() {
        RowReader reader = readerFor(ImportField.ITEM_TYPE, "Standard");

        assertThat(reader.itemType(ImportField.ITEM_TYPE)).isNull();
        assertThat(reader.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.code()).isEqualTo("UNKNOWN_ITEM_TYPE");
                    assertThat(issue.isError()).isFalse();
                });
    }

    /** "In Stock" is a stock word, not a status. Same treatment. */
    @Test
    void warnsRatherThanFailsOnAStatusItDoesNotRecognise() {
        RowReader reader = readerFor(ImportField.STATUS, "In Stock");

        assertThat(reader.status(ImportField.STATUS)).isNull();
        assertThat(reader.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.code()).isEqualTo("UNKNOWN_STATUS");
                    assertThat(issue.isError()).isFalse();
                });
    }

    @Test
    void reportsAValueTooLongForItsField() {
        RowReader reader = readerFor(ImportField.NAME, "x".repeat(500));

        assertThat(reader.text(ImportField.NAME, 200)).isNull();
        assertThat(reader.issues()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("VALUE_TOO_LONG"));
    }

    @Test
    void readsNothingForAnUnmatchedColumn() {
        RowReader reader = readerFor(ImportField.NAME, "Espresso");

        assertThat(reader.text(ImportField.SKU)).isNull();
        assertThat(reader.issues()).isEmpty();
    }

    @Test
    void keepsAPictureWeAreWillingToPublish() {
        RowReader reader = readerFor(ImportField.IMAGE_URL, " https://cdn.example.com/mug.jpg ");

        assertThat(reader.imageUrl(ImportField.IMAGE_URL))
                .isEqualTo("https://cdn.example.com/mug.jpg");
        assertThat(reader.issues()).isEmpty();
    }

    /**
     * The item survives its picture. A shop moving off an old system will have
     * links that rotted years ago, and losing the row over one would cost them
     * the name, the price and the stock count as well.
     */
    @Test
    void dropsAPictureWeWillNotPublishWithoutFailingTheRow() {
        RowReader reader = readerFor(ImportField.IMAGE_URL, "http://192.168.1.10/mug.jpg");

        assertThat(reader.imageUrl(ImportField.IMAGE_URL)).isNull();
        assertThat(reader.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.isError()).isFalse();
                    assertThat(issue.field()).isEqualTo(ImportField.IMAGE_URL.name());
                    assertThat(issue.message()).contains("without a picture");
                });
    }
}
