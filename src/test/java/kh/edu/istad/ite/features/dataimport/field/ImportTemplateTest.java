package kh.edu.istad.ite.features.dataimport.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import kh.edu.istad.ite.features.dataimport.parser.CsvSourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

class ImportTemplateTest {

    private final CsvSourceFileParser parser = new CsvSourceFileParser();

    private SourceFileParser.SourceHeader read(ImportTargetType targetType) {
        byte[] csv = ImportTemplate.csvFor(targetType).getBytes(StandardCharsets.UTF_8);

        return parser.readHeader(new ByteArrayInputStream(csv), 10);
    }

    /**
     * The promise the template makes: download it, fill it in, upload it, and
     * every column is already matched. A heading we wrote and then failed to
     * recognise would make the matching step look broken on the one file we
     * had complete control over.
     */
    @ParameterizedTest
    @EnumSource(ImportTargetType.class)
    void everyTemplateHeadingMatchesItsOwnField(ImportTargetType targetType) {
        SourceFileParser.SourceHeader header = read(targetType);
        List<ImportField> expected = ImportTemplate.columnsFor(targetType);

        assertThat(header.columns()).hasSameSizeAs(expected);

        for (int index = 0; index < expected.size(); index++) {
            ImportField field = expected.get(index);

            assertThat(ImportField.suggestFor(header.columns().get(index), targetType))
                    .as("%s heading %s", targetType, header.columns().get(index))
                    .contains(field);
        }
    }

    /** Every column offered must be one that kind of import can actually set. */
    @ParameterizedTest
    @EnumSource(ImportTargetType.class)
    void offersOnlyFieldsThatKindOfImportAccepts(ImportTargetType targetType) {
        assertThat(ImportTemplate.columnsFor(targetType))
                .allSatisfy(field -> assertThat(field.appliesTo(targetType)).isTrue());
    }

    /** A template missing a required column would fail the moment it was matched. */
    @ParameterizedTest
    @EnumSource(ImportTargetType.class)
    void includesEveryRequiredField(ImportTargetType targetType) {
        assertThat(ImportTemplate.columnsFor(targetType))
                .containsAll(ImportField.requiredFor(targetType));
    }

    /** Opening stock rows must say which item they are for. */
    @Test
    void includesAnIdentifierForOpeningStock() {
        assertThat(ImportTemplate.columnsFor(ImportTargetType.OPENING_STOCK))
                .containsAnyElementsOf(ImportField.identifiersFor(ImportTargetType.OPENING_STOCK));
    }

    @ParameterizedTest
    @EnumSource(ImportTargetType.class)
    void fillsEveryColumnOfEverySampleRow(ImportTargetType targetType) {
        SourceFileParser.SourceHeader header = read(targetType);

        assertThat(header.sample()).isNotEmpty();

        for (SourceRow row : header.sample()) {
            assertThat(row.values()).hasSameSizeAs(header.columns());
        }
    }

    /**
     * Excel on Windows reads a UTF-8 CSV as the local codepage unless it finds
     * a byte-order mark, and turns every accented name into mojibake. Our own
     * reader strips it again, which the heading assertions above rely on.
     */
    @ParameterizedTest
    @EnumSource(ImportTargetType.class)
    void startsWithAByteOrderMarkForExcel(ImportTargetType targetType) {
        assertThat(ImportTemplate.csvFor(targetType)).startsWith("﻿");
    }

    @Test
    void namesTheFileAfterWhatItIsFor() {
        List<String> names = new ArrayList<>();

        for (ImportTargetType targetType : ImportTargetType.values()) {
            names.add(ImportTemplate.fileNameFor(targetType));
        }

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allSatisfy(name -> assertThat(name).endsWith(".csv"));
    }
}
