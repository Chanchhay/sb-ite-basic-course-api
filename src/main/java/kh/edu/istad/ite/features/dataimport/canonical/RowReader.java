package kh.edu.istad.ite.features.dataimport.canonical;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads one row through the column matching, turning cell text into values.
 *
 * Every failure to read something is recorded rather than thrown. A single
 * unreadable price should cost the shop that one row and a message naming it,
 * not the whole import — and the message has to say which field, so it can be
 * shown against the right column on screen.
 *
 * Not thread-safe, and not meant to be: one of these is made per row.
 */
public class RowReader {

    private static final Set<String> TRUE_WORDS =
            Set.of("true", "yes", "y", "1", "active", "enabled", "on", "tracked");
    private static final Set<String> FALSE_WORDS =
            Set.of("false", "no", "n", "0", "inactive", "disabled", "off", "untracked");

    private final SourceRow row;
    private final MappingPlan plan;
    private final List<RowIssue> issues = new ArrayList<>();

    public RowReader(SourceRow row, MappingPlan plan) {
        this.row = row;
        this.plan = plan;
    }

    public List<RowIssue> issues() {
        return issues;
    }

    /** The raw text under a field's column, or null if unmatched or empty. */
    public String text(ImportField field) {
        String column = plan.columnFor(field);

        return column == null ? null : row.value(column);
    }

    public String text(ImportField field, int maxLength) {
        String value = text(field);

        if (value != null && value.length() > maxLength) {
            issues.add(RowIssue.error(
                    field.name(),
                    "VALUE_TOO_LONG",
                    field.getLabel() + " is longer than " + maxLength + " characters."
            ));
            return null;
        }

        return value;
    }

    /**
     * A picture's address, if it is one we are willing to publish.
     *
     * Never fatal. A link we will not use costs the item its photograph and
     * nothing else — the shop keeps its name, its price and its stock, and can
     * upload a picture afterwards. Refusing the whole row over a stale link
     * from an old system would be the worse trade by a distance.
     */
    public String imageUrl(ImportField field) {
        String raw = text(field);

        if (raw == null) {
            return null;
        }

        String value = raw.trim();

        if (value.isEmpty()) {
            return null;
        }

        Optional<ImageUrlPolicy.Rejection> rejection = ImageUrlPolicy.rejectionFor(value);

        if (rejection.isPresent()) {
            issues.add(RowIssue.warning(
                    field.name(),
                    rejection.get().code(),
                    rejection.get().message() + " The item will be imported without a picture."
            ));
            return null;
        }

        return value;
    }

    /**
     * A number, however the old system happened to write it.
     *
     * Currency symbols and spaces are dropped. Where both a dot and a comma
     * appear, whichever comes last is the decimal point — that reads
     * "1,234.56" and "1.234,56" the same way round, which no single separator
     * rule manages. A lone comma is a decimal point only when it is followed
     * by one or two digits, since "1,250" is far more often a thousand than a
     * fraction.
     */
    public BigDecimal number(ImportField field) {
        String raw = text(field);

        if (raw == null) {
            return null;
        }

        String cleaned = raw.replaceAll("[^0-9,.\\-]", "");

        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return reportUnreadableNumber(field, raw);
        }

        int lastDot = cleaned.lastIndexOf('.');
        int lastComma = cleaned.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            cleaned = lastDot > lastComma
                    ? cleaned.replace(",", "")
                    : cleaned.replace(".", "").replace(',', '.');
        } else if (lastComma >= 0) {
            int decimals = cleaned.length() - lastComma - 1;
            cleaned = decimals >= 1 && decimals <= 2 && cleaned.indexOf(',') == lastComma
                    ? cleaned.replace(',', '.')
                    : cleaned.replace(",", "");
        }

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return reportUnreadableNumber(field, raw);
        }
    }

    public Integer integer(ImportField field) {
        BigDecimal value = number(field);

        if (value == null) {
            return null;
        }

        try {
            return value.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException e) {
            issues.add(RowIssue.error(
                    field.name(),
                    "NUMBER_OUT_OF_RANGE",
                    field.getLabel() + " is too large."
            ));
            return null;
        }
    }

    public Boolean flag(ImportField field) {
        String raw = text(field);

        if (raw == null) {
            return null;
        }

        String value = raw.trim().toLowerCase();

        if (TRUE_WORDS.contains(value)) {
            return Boolean.TRUE;
        }
        if (FALSE_WORDS.contains(value)) {
            return Boolean.FALSE;
        }

        issues.add(RowIssue.warning(
                field.name(),
                "NOT_A_YES_OR_NO",
                "\"" + raw + "\" is not a yes or no, so " + field.getLabel().toLowerCase()
                        + " will be decided by the item type."
        ));
        return null;
    }

    public ItemType itemType(ImportField field) {
        String raw = text(field);

        if (raw == null) {
            return null;
        }

        return switch (ImportField.normalize(raw)) {
            case "physical", "goods", "good", "product", "stock", "stocked", "inventory" -> ItemType.PHYSICAL;
            case "service", "services", "labour", "labor" -> ItemType.SERVICE;
            case "digital", "download", "downloadable", "virtual" -> ItemType.DIGITAL;
            default -> {
                /*
                 * Every old system has its own word for this — Standard,
                 * Stock, Goods, Normal. Refusing the row over it would fail an
                 * entire catalogue on a column the shop barely cares about, so
                 * it falls back and says so. The warning is what makes that
                 * honest rather than silent.
                 */
                issues.add(RowIssue.warning(
                        field.name(),
                        "UNKNOWN_ITEM_TYPE",
                        "\"" + raw + "\" is not a type we recognise, so this will be imported"
                                + " as a physical item."
                ));
                yield null;
            }
        };
    }

    public ItemStatus status(ImportField field) {
        String raw = text(field);

        if (raw == null) {
            return null;
        }

        String value = ImportField.normalize(raw);

        if (TRUE_WORDS.contains(value) || value.equals("active")) {
            return ItemStatus.ACTIVE;
        }
        if (FALSE_WORDS.contains(value) || value.equals("inactive") || value.equals("archived")) {
            return ItemStatus.INACTIVE;
        }

        issues.add(RowIssue.warning(
                field.name(),
                "UNKNOWN_STATUS",
                "\"" + raw + "\" is not a status we recognise, so this will be imported as active."
        ));
        return null;
    }

    private BigDecimal reportUnreadableNumber(ImportField field, String raw) {
        issues.add(RowIssue.error(
                field.name(),
                "NOT_A_NUMBER",
                "\"" + raw + "\" is not a number for " + field.getLabel() + "."
        ));
        return null;
    }
}
