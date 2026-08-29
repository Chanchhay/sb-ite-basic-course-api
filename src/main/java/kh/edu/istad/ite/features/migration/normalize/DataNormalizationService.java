package kh.edu.istad.ite.features.migration.normalize;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportFieldType;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a customer's values into FluxiBiz's, deterministically and visibly.
 *
 * Every rule here answers the same question: is this unambiguous? Trimming
 * runs of whitespace is; deciding that "SACK" means a count is not. So the
 * safe transformations are applied and reported in bulk, and everything else
 * becomes a decision for a person — which is what keeps a migration something
 * an operator can vouch for afterwards.
 *
 * Nothing here renames a customer's products or converts their money. A price
 * of "$0.75" becomes 0.75 because the digits are the same number; it never
 * becomes anything else, whatever currency the shop turns out to use.
 */
@Component
public class DataNormalizationService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    private static final Set<String> TRUE_WORDS =
            Set.of("true", "yes", "y", "1", "active", "enabled", "on", "tracked", "t");
    private static final Set<String> FALSE_WORDS =
            Set.of("false", "no", "n", "0", "inactive", "disabled", "off", "untracked", "f");

    private static final Set<String> PHYSICAL =
            Set.of("physical", "goods", "good", "product", "stockitem", "stock", "stocked", "inventory", "normal");
    private static final Set<String> SERVICE = Set.of("service", "services", "labour", "labor");
    private static final Set<String> DIGITAL =
            Set.of("digital", "digitalproduct", "download", "downloadable", "virtual", "ebook");

    /**
     * Reads one cell for the field it was mapped to.
     *
     * The field decides the rules: the same "0" is a quantity under Opening
     * Stock and a no under Track Stock, and nothing but the mapping can tell
     * them apart.
     */
    public Normalized normalize(ImportField field, String raw) {
        String text = tidy(raw);

        if (text == null) {
            return Normalized.asIs(null);
        }

        Normalized tidied = text.equals(raw)
                ? Normalized.asIs(text)
                : Normalized.changed(text, "Extra spaces removed");

        return switch (field.getType()) {
            case MONEY, NUMBER -> number(text, tidied);
            case BOOLEAN -> flag(text);
            case ENUM -> field == ImportField.ITEM_TYPE ? itemType(text) : tidied;
            case TEXT -> tidied;
        };
    }

    /**
     * Whitespace and control characters, and nothing else.
     *
     * "   Coca   Cola   " is "Coca Cola" — the shop meant one thing and their
     * old system stored it untidily. What this must never do is decide that
     * "Coca-Cola" and "Coca Cola" are the same product; that is a judgement
     * about their catalogue, and it belongs in the duplicate review where
     * somebody can disagree with it.
     */
    public String tidy(String raw) {
        if (raw == null) {
            return null;
        }

        String cleaned = WHITESPACE.matcher(CONTROL.matcher(raw).replaceAll("")).replaceAll(" ").trim();

        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * A number, however the old system dressed it.
     *
     * Currency symbols and codes are stripped rather than interpreted — "0.75
     * USD" is 0.75, and whether the shop's base currency is dollars is a
     * question for FluxiBiz's own settings, not for a spreadsheet cell. A value
     * with no digits in it at all is refused rather than read as zero: an
     * empty price and a free item are different claims.
     */
    private Normalized number(String text, Normalized tidied) {
        String stripped = text.replaceAll("(?i)[\\p{Sc}]|\\b(usd|khr|eur|gbp|riel|dollars?)\\b", "").trim();
        String cleaned = stripped.replaceAll("[^0-9,.\\-]", "");

        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return Normalized.unreadable("\"" + text + "\" is not a number");
        }

        int lastDot = cleaned.lastIndexOf('.');
        int lastComma = cleaned.lastIndexOf(',');

        /*
         * Whichever separator comes last is the decimal point, which reads
         * "1,234.56" and "1.234,56" the same way round. A lone comma is only a
         * decimal point when one or two digits follow it, since "1,250" is far
         * more often a thousand than a fraction.
         */
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
            java.math.BigDecimal parsed = new java.math.BigDecimal(cleaned);
            String value = parsed.toPlainString();

            return value.equals(text)
                    ? tidied
                    : Normalized.changed(value, "Price and number formatting");
        } catch (NumberFormatException e) {
            return Normalized.unreadable("\"" + text + "\" is not a number");
        }
    }

    /**
     * Yes or no — and nothing in between.
     *
     * An unrecognised word becomes a question rather than a no. A shop whose
     * tracking column says "STK" means something by it, and quietly reading
     * that as "do not count this" would turn their entire stocked catalogue
     * into untracked items without a word.
     */
    private Normalized flag(String text) {
        String word = text.toLowerCase();

        if (TRUE_WORDS.contains(word)) {
            return word.equals("yes") ? Normalized.asIs("Yes") : Normalized.changed("Yes", "Yes/no wording");
        }
        if (FALSE_WORDS.contains(word)) {
            return word.equals("no") ? Normalized.asIs("No") : Normalized.changed("No", "Yes/no wording");
        }

        return Normalized.unreadable("\"" + text + "\" is not a yes or no");
    }

    private Normalized itemType(String text) {
        String word = ImportField.normalize(text);

        if (PHYSICAL.contains(word)) {
            return Normalized.changed("Physical", "Item type wording");
        }
        if (SERVICE.contains(word)) {
            return Normalized.changed("Service", "Item type wording");
        }
        if (DIGITAL.contains(word)) {
            return Normalized.changed("Digital", "Item type wording");
        }

        return Normalized.unreadable("\"" + text + "\" is not an item type we recognise");
    }
}
