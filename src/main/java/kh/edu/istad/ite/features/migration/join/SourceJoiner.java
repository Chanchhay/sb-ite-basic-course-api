package kh.edu.istad.ite.features.migration.join;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.entity.AssistedMigrationSource;
import kh.edu.istad.ite.features.migration.entity.MigrationSourceRelationship;
import kh.edu.istad.ite.features.migration.resolve.FieldValue;
import kh.edu.istad.ite.features.migration.resolve.ResolvedRecord;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.MigrationJoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gathers a migration's files into one record per thing the customer owns.
 *
 * The main file decides what exists — one of its rows is one item — and the
 * others only fill in what it did not say. That asymmetry is deliberate: a
 * stock export mentioning a code the product list has never heard of is a
 * discrepancy to report, not a licence to invent an item out of a quantity.
 *
 * Values are offered rather than assigned, so the first file to answer keeps
 * the field. The main file therefore always beats a joined one, which is the
 * priority the whole missing-field strategy rests on — what the record itself
 * says outranks what another file says about it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SourceJoiner {

    /**
     * @param records  one per thing that will become an item
     * @param findings what was odd about the joining itself, to be reported
     */
    public record JoinedRecords(
            List<ResolvedRecord> records,
            List<TransformResult.Finding> findings
    ) {
    }

    public JoinedRecords join(
            AssistedMigrationSource primary,
            List<AssistedMigrationSource> sources,
            Map<UUID, List<SourceRow>> rowsBySource,
            List<MigrationSourceRelationship> relationships
    ) {
        List<SourceRow> primaryRows = rowsBySource.getOrDefault(primary.getId(), List.of());
        Map<UUID, AssistedMigrationSource> byId = new LinkedHashMap<>();

        sources.forEach(source -> byId.put(source.getId(), source));

        List<Link> links = linksAnchoredOnPrimary(primary, relationships, byId, rowsBySource);
        List<ResolvedRecord> records = new ArrayList<>();
        List<TransformResult.Finding> findings = new ArrayList<>();
        Map<UUID, Integer> unmatchedBySource = new LinkedHashMap<>();

        for (SourceRow row : primaryRows) {
            ResolvedRecord record = ResolvedRecord.empty(row.rowNumber());

            apply(record, row, primary, true);

            boolean dropped = false;

            for (Link link : links) {
                String key = JoinKeys.of(row.value(link.leftColumn()));
                List<SourceRow> matches = key == null ? null : link.index().get(key);

                if (matches == null || matches.isEmpty()) {
                    unmatchedBySource.merge(link.source().getId(), 1, Integer::sum);

                    if (link.joinType() == MigrationJoinType.INNER) {
                        dropped = true;
                        break;
                    }

                    continue;
                }

                /*
                 * The first match, and only the first. Where a key repeats on
                 * the right — several stock lines for one product — taking one
                 * is the only choice that keeps one item as one item. That the
                 * key repeats at all is said out loud in the join quality
                 * before the operator approves it, which is where the decision
                 * belongs.
                 */
                apply(record, matches.getFirst(), link.source(), false);
            }

            if (!dropped && !record.isEmpty()) {
                records.add(record);
            }
        }

        unmatchedBySource.forEach((sourceId, count) -> {
            AssistedMigrationSource source = byId.get(sourceId);

            findings.add(new TransformResult.Finding(
                    "SOURCE_NOT_MATCHED",
                    null,
                    source == null ? sourceId.toString() : source.getFileName(),
                    count + " row" + (count == 1 ? "" : "s") + " of the main file found nothing in "
                            + (source == null ? "a removed file" : source.getFileName())
                            + ". Those records keep whatever the main file gave them.",
                    0,
                    false));
        });

        return new JoinedRecords(records, findings);
    }

    /**
     * Reads one row through its own file's mapping.
     *
     * Each file carries its own mapping because headings collide — two exports
     * from the same system will both have a {@code product_code}, and a
     * mapping keyed by heading alone could not say which file's was meant.
     */
    private void apply(
            ResolvedRecord record,
            SourceRow row,
            AssistedMigrationSource source,
            boolean isPrimary
    ) {
        source.getColumnMappings().forEach((heading, fieldName) -> {
            ImportField field = fieldOf(fieldName, source);

            if (field == null) {
                return;
            }

            String value = row.value(heading);

            if (value == null || value.isBlank()) {
                return;
            }

            record.offer(field, isPrimary
                    ? FieldValue.direct(value, source.getFileName(), heading, row.rowNumber())
                    : FieldValue.joined(value, source.getFileName(), heading, row.rowNumber()));
        });
    }

    private ImportField fieldOf(String name, AssistedMigrationSource source) {
        try {
            return ImportField.valueOf(name);
        } catch (IllegalArgumentException e) {
            log.warn("Source {} maps a column to unknown field {}", source.getId(), name);
            return null;
        }
    }

    /**
     * The joins, all pointed the same way, with their right-hand sides indexed.
     *
     * An operator may have named the relationship in either direction; what
     * matters downstream is that the main file is on the left. A join
     * connecting two files that are both secondary is dropped rather than
     * chained — nobody has sent that shape, and guessing at join order is how
     * a migration silently multiplies rows.
     */
    private List<Link> linksAnchoredOnPrimary(
            AssistedMigrationSource primary,
            List<MigrationSourceRelationship> relationships,
            Map<UUID, AssistedMigrationSource> byId,
            Map<UUID, List<SourceRow>> rowsBySource
    ) {
        List<Link> links = new ArrayList<>();

        for (MigrationSourceRelationship relationship : relationships) {
            UUID leftId = relationship.getLeftSourceId();
            UUID rightId = relationship.getRightSourceId();

            String leftColumn = relationship.getLeftColumn();
            String rightColumn = relationship.getRightColumn();

            if (rightId.equals(primary.getId())) {
                UUID swappedId = leftId;
                String swappedColumn = leftColumn;

                leftId = rightId;
                leftColumn = rightColumn;
                rightId = swappedId;
                rightColumn = swappedColumn;
            }

            if (!leftId.equals(primary.getId())) {
                log.warn("Migration {} has a join between two secondary sources; ignoring it",
                        relationship.getMigration().getId());
                continue;
            }

            AssistedMigrationSource right = byId.get(rightId);

            if (right == null) {
                continue;
            }

            links.add(new Link(
                    right,
                    leftColumn,
                    JoinKeys.index(rowsBySource.getOrDefault(rightId, List.of()), rightColumn),
                    relationship.getJoinType()));
        }

        return links;
    }

    /** One approved join, with the file it reaches already in a hash map. */
    private record Link(
            AssistedMigrationSource source,
            String leftColumn,
            Map<String, List<SourceRow>> index,
            MigrationJoinType joinType
    ) {
    }
}
