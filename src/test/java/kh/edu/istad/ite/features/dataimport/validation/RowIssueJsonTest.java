package kh.edu.istad.ite.features.dataimport.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import kh.edu.istad.ite.shared.enums.ImportIssueSeverity;

/**
 * That a staged row's notes survive being written to the database and read
 * back.
 *
 * They live in a JSON column, and Hibernate round-trips such a column through
 * Jackson when it flushes — so a shape Jackson can write but not read is not a
 * cosmetic problem. It failed every row of every import: {@code isError()} was
 * written out as an {@code "error"} property that the record's own constructor
 * then refused, and the whole check collapsed with a message about the file
 * being unreadable. These tests are the regression.
 */
class RowIssueJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void survivesBeingWrittenAndReadBack() throws Exception {
        RowIssue issue = RowIssue.error("SKU", "DUPLICATE_SKU", "SKU already exists");

        RowIssue readBack = mapper.readValue(mapper.writeValueAsString(issue), RowIssue.class);

        assertThat(readBack).isEqualTo(issue);
        assertThat(readBack.isError()).isTrue();
    }

    /** The list is the shape actually stored on a row. */
    @Test
    void survivesAsAList() throws Exception {
        List<RowIssue> issues = List.of(
                RowIssue.error("NAME", "MISSING_NAME", "An item needs a name."),
                RowIssue.warning("COST_PRICE", "COST_ASSUMED_ZERO", "No cost price given."));

        List<RowIssue> readBack = mapper.readValue(
                mapper.writeValueAsString(issues), new TypeReference<>() {});

        assertThat(readBack).isEqualTo(issues);
    }

    /** The derived flag must not be written; it is what the constructor chokes on. */
    @Test
    void doesNotWriteTheDerivedErrorFlag() throws Exception {
        String json = mapper.writeValueAsString(
                RowIssue.warning("UNIT", "UNKNOWN_UNIT", "Not one of your units."));

        assertThat(json).doesNotContain("\"error\"");
        assertThat(json).contains("\"severity\":\"WARNING\"");
    }

    /** A note written by an older deploy must still be readable after a change. */
    @Test
    void toleratesAPropertyItNoLongerKnows() throws Exception {
        String json = """
                {"field":"SKU","code":"DUPLICATE_SKU","message":"Already exists",
                 "severity":"ERROR","somethingRetired":true}
                """;

        RowIssue readBack = mapper.readValue(json, RowIssue.class);

        assertThat(readBack.severity()).isEqualTo(ImportIssueSeverity.ERROR);
        assertThat(readBack.code()).isEqualTo("DUPLICATE_SKU");
    }
}
