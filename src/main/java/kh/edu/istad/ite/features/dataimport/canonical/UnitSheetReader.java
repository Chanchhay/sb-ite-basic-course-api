package kh.edu.istad.ite.features.dataimport.canonical;

import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the Units sheet a workbook may carry beside its items.
 *
 * Forgiving about the headings, because this sheet is filled in by hand more
 * than any other: Name, Short Symbol, Type and Note are matched however they
 * are capitalised or spaced. Unforgiving about the type, because that is the
 * one thing nothing else can supply and guessing it would silently corrupt
 * every quantity the unit ever counts.
 *
 * A row too broken to read is skipped rather than refused. The units that
 * matter are the ones the item rows actually name, and those come back as
 * "not declared" further down the line with a message pointing at the sheet —
 * which is more use than failing the upload over a stray line.
 */
@Component
public class UnitSheetReader {

    /** More than any shop has, and enough that nobody meets the ceiling. */
    private static final int MAX_UNITS = 200;

    private static final List<String> NAME_HEADINGS = List.of("name", "unit", "unitname");
    private static final List<String> SYMBOL_HEADINGS =
            List.of("shortsymbol", "symbol", "short", "abbreviation", "code");
    private static final List<String> TYPE_HEADINGS =
            List.of("type", "category", "measures", "unittype");
    private static final List<String> NOTE_HEADINGS = List.of("note", "notes", "remark", "comment");

    public List<DeclaredUnit> read(SourceFileParser parser, InputStream input) {
        List<SourceRow> rows = parser.readNamedSheet(
                input, XlsxSourceFileParser.UNITS_SHEET, MAX_UNITS);

        Map<String, DeclaredUnit> byName = new LinkedHashMap<>();
        List<DeclaredUnit> declared = new ArrayList<>();

        for (SourceRow row : rows) {
            DeclaredUnit unit = toUnit(row);

            if (unit == null) {
                continue;
            }

            /*
             * The same unit written twice is a copy-paste, not a disagreement
             * worth stopping for — the first wins and the second is dropped, so
             * a sheet listing Piece three times still creates one unit.
             */
            if (byName.putIfAbsent(unit.name().trim().toLowerCase(), unit) == null) {
                declared.add(unit);
            }
        }

        return declared;
    }

    private DeclaredUnit toUnit(SourceRow row) {
        String name = valueOf(row, NAME_HEADINGS);

        if (name == null || name.isBlank()) {
            return null;
        }

        UnitCategory category = categoryOf(valueOf(row, TYPE_HEADINGS));

        if (category == null) {
            /*
             * No type, no unit. Everything else about a unit can be inferred
             * or left out; what it measures cannot, and a Kilogram counted
             * rather than weighed is worse than one that never arrived.
             */
            return null;
        }

        return new DeclaredUnit(
                name.trim(),
                blankToNull(valueOf(row, SYMBOL_HEADINGS)),
                category,
                blankToNull(valueOf(row, NOTE_HEADINGS))
        );
    }

    private UnitCategory categoryOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toUpperCase();

        for (UnitCategory category : UnitCategory.values()) {
            if (category.name().equals(normalized)) {
                return category;
            }
        }

        return null;
    }

    /** The first column whose heading is one of these, flattened for comparison. */
    private String valueOf(SourceRow row, List<String> headings) {
        for (Map.Entry<String, String> cell : row.values().entrySet()) {
            String heading = cell.getKey() == null
                    ? ""
                    : cell.getKey().toLowerCase().replaceAll("[^a-z0-9]", "");

            if (headings.contains(heading)) {
                return cell.getValue();
            }
        }

        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
