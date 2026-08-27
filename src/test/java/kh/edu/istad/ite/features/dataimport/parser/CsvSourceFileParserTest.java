package kh.edu.istad.ite.features.dataimport.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CsvSourceFileParserTest {

    private final CsvSourceFileParser parser = new CsvSourceFileParser();

    private InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private List<SourceRow> readAll(String content, int limit) {
        List<SourceRow> rows = new ArrayList<>();
        parser.readRows(csv(content), limit, rows::add);
        return rows;
    }

    /**
     * The whole point of the row number: a message about row 3 has to point at
     * the third line the user sees in their spreadsheet, headings included.
     */
    @Test
    void numbersRowsTheWayASpreadsheetDoes() {
        List<SourceRow> rows = readAll("""
                name,sku
                Espresso,ESP-1
                Latte,LAT-2
                """, 100);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
        assertThat(rows.get(1).rowNumber()).isEqualTo(3);
    }

    @Test
    void readsHeadingsAndValues() {
        List<SourceRow> rows = readAll("""
                name,sku
                Espresso,ESP-1
                """, 100);

        assertThat(rows.getFirst().value("name")).isEqualTo("Espresso");
        assertThat(rows.getFirst().value("sku")).isEqualTo("ESP-1");
    }

    /** Excel's UTF-8 export leads with a byte-order mark; it must not stick to the heading. */
    @Test
    void stripsTheByteOrderMarkFromTheFirstHeading() {
        SourceFileParser.SourceHeader header = parser.readHeader(csv("﻿name,sku\nEspresso,ESP-1\n"), 5);

        assertThat(header.columns()).containsExactly("name", "sku");
    }

    @Test
    void keepsCommasThatAreInsideQuotedValues() {
        List<SourceRow> rows = readAll("""
                name,note
                "Beans, roasted",fine
                """, 100);

        assertThat(rows.getFirst().value("name")).isEqualTo("Beans, roasted");
        assertThat(rows.getFirst().value("note")).isEqualTo("fine");
    }

    /**
     * Blank lines are dropped, but they still take up a line — so the row
     * after one keeps the number the user would count to.
     */
    @Test
    void skipsBlankLinesWithoutRenumberingWhatFollows() {
        List<SourceRow> rows = readAll("name,sku\nEspresso,ESP-1\n\nLatte,LAT-2\n", 100);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).value("name")).isEqualTo("Latte");
    }

    /** One row past the limit, so the caller can tell "at the cap" from "over it". */
    @Test
    void stopsOneRowPastTheLimit() {
        List<SourceRow> rows = readAll("name\na\nb\nc\nd\ne\n", 2);

        assertThat(rows).hasSize(3);
    }

    @Test
    void readsHeaderWithOnlyTheRequestedSample() {
        SourceFileParser.SourceHeader header =
                parser.readHeader(csv("name\na\nb\nc\nd\n"), 2);

        assertThat(header.columns()).containsExactly("name");
        assertThat(header.sample()).hasSize(2);
    }

    @Test
    void refusesAFileWhoseFirstLineIsEmpty() {
        assertThatThrownBy(() -> parser.readHeader(csv(",,\na,b,c\n"), 5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("column headings");
    }

    @Test
    void handlesOnlyCsvFiles() {
        assertThat(parser.supports("catalogue.csv")).isTrue();
        assertThat(parser.supports("catalogue.CSV")).isTrue();
        assertThat(parser.supports("catalogue.xlsx")).isFalse();
    }
}
