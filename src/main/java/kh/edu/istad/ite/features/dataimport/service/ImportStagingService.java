package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemVariantRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.dataimport.canonical.CanonicalRecordMapper;
import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParserRegistry;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportJobRepository;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.features.dataimport.validation.ImportValidatorRegistry;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.features.dataimport.validation.RowVerdict;
import kh.edu.istad.ite.features.dataimport.validation.ValidationContext;
import kh.edu.istad.ite.features.inventory.repository.StockEntryRepository;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the file and stages every row, in a transaction of its own.
 *
 * A bean apart from the service that orchestrates checking, and that
 * separation is the whole point. Staging ten thousand rows is one transaction;
 * if any part of it fails, that transaction is rolled back — and anything the
 * orchestrator wanted to record about the failure has to be written somewhere
 * that rollback cannot reach. Keeping the two in one bean is what stranded
 * jobs in a checking state they could never leave.
 *
 * Nothing here touches the catalogue. The worst an interruption costs is a
 * check that has to be run again.
 */
@Service
@RequiredArgsConstructor
public class ImportStagingService {

    /** How many staged rows to write at a time while reading a large file. */
    private static final int STAGING_BATCH = 500;

    private final ImportJobRepository importJobRepository;
    private final ImportRowRepository importRowRepository;
    private final SourceFileParserRegistry parserRegistry;
    private final CanonicalRecordMapper recordMapper;
    private final ImportValidatorRegistry validatorRegistry;
    private final MinioService minioService;

    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final UnitRepository unitRepository;
    private final StockEntryRepository stockEntryRepository;

    @Transactional
    public ImportTotals stage(UUID jobId, int maxRows) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();

        importRowRepository.deleteByImportJobId(jobId);

        /*
         * Re-read after the delete. That query clears the persistence context
         * to keep it honest about what it just removed, which leaves the job
         * we were holding detached — and every staged row points at it.
         */
        ImportJob attached = importJobRepository.findById(jobId).orElseThrow();

        MappingPlan plan = MappingPlan.from(attached);
        ValidationContext context =
                loadContext(attached.getBusiness().getId(), attached.getDeclaredUnits());

        ImportTotals totals = stageAndJudge(attached, plan, context, maxRows);
        totals.entities = context.distinctGroups();

        return totals;
    }

    private ImportTotals stageAndJudge(
            ImportJob job,
            MappingPlan plan,
            ValidationContext context,
            int maxRows
    ) {
        ImportTotals totals = new ImportTotals();
        List<ImportRow> batch = new ArrayList<>();

        try (InputStream input = minioService.readImportFile(job.getStorageObjectKey())) {
            parserRegistry.parserFor(job.getSourceFileName()).readRows(input, maxRows, sourceRow -> {
                totals.total++;

                /*
                 * The reader hands over one row past the limit before it
                 * stops, which is what tells a file of twenty thousand from
                 * one of twenty thousand and one. That extra row is counted
                 * and then thrown away with the rest.
                 */
                if (totals.total > maxRows) {
                    return;
                }

                batch.add(judge(job, sourceRow, plan, context, totals));

                if (batch.size() >= STAGING_BATCH) {
                    importRowRepository.saveAll(batch);
                    batch.clear();
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!batch.isEmpty()) {
            importRowRepository.saveAll(batch);
        }

        return totals;
    }

    private ImportRow judge(
            ImportJob job,
            SourceRow sourceRow,
            MappingPlan plan,
            ValidationContext context,
            ImportTotals totals
    ) {
        CanonicalRecordMapper.MappedRow mapped = recordMapper.map(sourceRow, plan);

        ImportRow row = new ImportRow();
        row.setImportJob(job);
        row.setRowNumber(sourceRow.rowNumber());
        row.setRawData(sourceRow.values());

        ImportRecord record = mapped.record();
        row.setNormalizedData(record == null ? null : record.normalized());
        row.setExternalId(record == null ? null : trimExternalId(record.externalId()));

        /*
         * A row whose values could not even be read is refused here and never
         * reaches a validator: the rules are written against a record, and
         * there is nothing yet to judge.
         */
        boolean unreadable = mapped.issues().stream().anyMatch(RowIssue::isError);

        if (record == null || unreadable) {
            row.setStatus(ImportRowStatus.INVALID);
            row.setIssues(mapped.issues());
            totals.invalid++;
            return row;
        }

        RowVerdict verdict = validatorRegistry
                .forTarget(job.getTargetType())
                .validate(record, sourceRow.rowNumber(), context, plan);

        List<RowIssue> issues = new ArrayList<>(mapped.issues());
        issues.addAll(verdict.issues());

        row.setStatus(verdict.status());
        row.setIssues(issues);
        row.setCommittedEntityId(verdict.matchedEntityId());

        if (record instanceof ItemImportRecord item && item.hasOptions()) {
            row.setGroupKey(item.groupingKey());
        }

        switch (verdict.status()) {
            case VALID -> {
                totals.valid++;
                if (opensStock(record, job.getTargetType())) {
                    totals.openingStock++;
                }
            }
            case DUPLICATE -> totals.duplicate++;
            default -> totals.invalid++;
        }

        return row;
    }

    /** Whether committing this row would also put a quantity on a shelf. */
    private boolean opensStock(ImportRecord record, ImportTargetType targetType) {
        if (targetType == ImportTargetType.OPENING_STOCK) {
            return true;
        }

        return record instanceof ItemImportRecord item && item.hasOpeningStock();
    }

    /**
     * The shop's catalogue as it stands, read once.
     *
     * Every row is then judged against memory. The alternative — asking the
     * database per row whether this SKU is taken — turns a ten thousand row
     * file into tens of thousands of queries, and is the difference between an
     * import that takes seconds and one that takes an afternoon.
     */
    private ValidationContext loadContext(UUID businessId, List<DeclaredUnit> declaredUnits) {
        List<ItemGroup> allGroups =
                new ArrayList<>(itemGroupRepository.findByBusinessIdAndParentIsNullOrderByNameAsc(businessId));
        allGroups.addAll(itemGroupRepository.findByBusinessIdAndParentIsNotNullOrderByNameAsc(businessId));

        List<Unit> units = unitRepository.findByBusinessIsNullOrBusinessIdOrderByNameAsc(businessId);
        List<Item> items = itemRepository.findAllByBusinessIdOrderByNameAsc(businessId);
        Set<UUID> withStock = stockEntryRepository.findItemIdsWithStockHistory(businessId);
        Set<UUID> withVariants = itemVariantRepository.findItemIdsWithVariants(businessId);

        return new ValidationContext(
                businessId, allGroups, units, items, withStock, withVariants, declaredUnits);
    }

    private String trimExternalId(String value) {
        if (value == null) {
            return null;
        }

        return value.length() <= 200 ? value : value.substring(0, 200);
    }
}
