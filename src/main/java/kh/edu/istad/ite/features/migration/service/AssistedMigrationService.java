package kh.edu.istad.ite.features.migration.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportMappingRequest;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportSample;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParserRegistry;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.service.ImportJobService;
import kh.edu.istad.ite.features.migration.entity.MigrationEntityLink;
import kh.edu.istad.ite.features.migration.repository.MigrationEntityLinkRepository;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.MigrationEntityType;
import kh.edu.istad.ite.features.migration.entity.AssistedMigration;
import kh.edu.istad.ite.features.migration.entity.MigrationIssue;
import kh.edu.istad.ite.features.migration.mapping.ColumnMappingService;
import kh.edu.istad.ite.features.migration.mapping.ColumnSuggestion;
import kh.edu.istad.ite.features.migration.profile.SourceProfilingService;
import kh.edu.istad.ite.features.migration.repository.AssistedMigrationRepository;
import kh.edu.istad.ite.features.migration.repository.MigrationIssueRepository;
import kh.edu.istad.ite.features.migration.duplicate.DuplicateDetectionService;
import kh.edu.istad.ite.features.migration.normalize.CategoryGrouping;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.PreparedWorkbookWriter;
import kh.edu.istad.ite.features.migration.transform.SourceTransformer;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.AssistedMigrationStatus;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.MigrationIssueSeverity;
import kh.edu.istad.ite.shared.enums.MigrationIssueStatus;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a customer's own export through to a prepared FluxiBiz import.
 *
 * The order of operations is the feature: read the file, describe it, guess
 * what its columns are, let an operator correct that, turn it into FluxiBiz's
 * words, put what remains ambiguous to a person once, and only then produce an
 * import job. Nothing here writes to a catalogue — the last step hands an
 * ordinary workbook to the ordinary importer, which checks and commits it the
 * way it does for every shop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistedMigrationService {

    /** More than any real export, and small enough to hold while transforming. */
    private static final int MAX_ROWS = 20_000;
    private static final int SAMPLE_ROWS = 20;

    private final AssistedMigrationRepository migrationRepository;
    private final MigrationIssueRepository issueRepository;
    private final SourceFileParserRegistry parsers;
    private final SourceProfilingService profiler;
    private final ColumnMappingService columnMapper;
    private final SourceTransformer transformer;
    private final PreparedWorkbookWriter workbookWriter;
    private final ImportJobService importJobService;
    private final MinioService minioService;
    private final BusinessHelper businessHelper;
    private final MigrationEntityLinkRepository linkRepository;
    private final ImportRowRepository importRowRepository;
    private final DuplicateDetectionService duplicates;
    private final CategoryGrouping categories;
    private final ItemRepository itemRepository;

    // --- starting ------------------------------------------------------------------

    @Transactional
    public AssistedMigration create(UUID businessId, ImportTargetType targetType, LocalDateTime snapshotAt) {
        AssistedMigration migration = new AssistedMigration();

        migration.setBusiness(businessHelper.findBusinessForOperator(businessId));
        migration.setTargetImportType(targetType == null ? ImportTargetType.ITEM : targetType);
        migration.setSnapshotAt(snapshotAt == null ? LocalDateTime.now() : snapshotAt);

        return migrationRepository.save(migration);
    }

    /**
     * Stores the customer's file exactly as it arrived, then reads it.
     *
     * The raw file is never rewritten. Everything this feature works out lives
     * beside it, so an operator who suspects the pipeline misread something can
     * always go back to what the customer actually sent.
     */
    @Transactional
    public AssistedMigration attachFile(UUID businessId, UUID migrationId, MultipartFile file) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to migrate.");
        }

        SourceFileParser parser = parsers.parserFor(file.getOriginalFilename());
        SourceFileParser.SourceHeader header = readHeader(parser, file);

        migration.setSourceType(parser.sourceType());
        migration.setSourceFileName(file.getOriginalFilename());
        migration.setSourceFileSize(file.getSize());
        migration.setSourceColumns(header.columns());
        migration.setColumnCount(header.columns().size());
        migration.setRawObjectKey(minioService.uploadImportFile(file, businessId));
        migration.setStatus(AssistedMigrationStatus.UPLOADED);

        return migrationRepository.save(migration);
    }

    /**
     * Every migration for this shop, newest first.
     *
     * An operator picks a job up days after starting it, and often somebody
     * else's. The list is how they find the one they mean.
     */
    public Page<AssistedMigration> findAll(UUID businessId, Pageable pageable) {
        businessHelper.findBusinessForOperator(businessId);

        return migrationRepository.findByBusinessIdOrderByCreatedDateDesc(businessId, pageable);
    }

    // --- understanding -------------------------------------------------------------

    /** What every column contains, and what each one probably is. */
    public record Analysis(
            SourceProfilingService.SourceProfile profile,
            List<ColumnSuggestion> suggestions
    ) {
    }

    @Transactional
    public Analysis analyze(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);
        List<SourceRow> rows = readRows(migration);

        SourceProfilingService.SourceProfile profile =
                profiler.profile(migration.getSourceColumns(), rows);

        List<ColumnSuggestion> suggestions =
                columnMapper.suggest(profile.columns(), migration.getTargetImportType());

        migration.setRowCount(profile.rows());
        migration.setStatus(AssistedMigrationStatus.ANALYZED);

        /*
         * Only the confident ones are filled in. A suggestion the operator has
         * to check is worth showing and not worth pre-selecting: accepting a
         * screen of amber ticks is exactly how a column gets mapped wrongly.
         */
        Map<String, String> preselected = new LinkedHashMap<>();

        suggestions.stream()
                .filter(ColumnSuggestion::isHigh)
                .forEach(s -> preselected.put(s.sourceColumn(), s.target().name()));

        if (migration.getColumnMappings().isEmpty()) {
            migration.setColumnMappings(preselected);
        }

        migrationRepository.save(migration);

        return new Analysis(profile, suggestions);
    }

    @Transactional
    public AssistedMigration saveMapping(UUID businessId, UUID migrationId, Map<String, String> mappings) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        Map<String, String> cleaned = new LinkedHashMap<>();

        mappings.forEach((column, field) -> {
            if (field != null && !field.isBlank()) {
                cleaned.put(column, field);
            }
        });

        migration.setColumnMappings(cleaned);
        migration.setStatus(AssistedMigrationStatus.ANALYZED);

        return migrationRepository.save(migration);
    }

    // --- transforming --------------------------------------------------------------

    /**
     * Reads the whole file into FluxiBiz's terms and files what is left over.
     *
     * Safe to run again. Findings from the last run are cleared and re-derived;
     * decisions an operator already made are kept and reused, so correcting one
     * column does not cost them forty answers.
     */
    @Transactional
    public AssistedMigration transform(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        requireMapping(migration);

        migration.setStatus(AssistedMigrationStatus.TRANSFORMING);

        TransformResult result = transformRows(migration);

        issueRepository.deleteByMigrationIdAndStatus(migrationId, MigrationIssueStatus.OPEN);
        record(migration, result.findings());

        long blocking = issueRepository.findByMigrationIdOrderBySeverityDescAffectedRowsDesc(migrationId)
                .stream()
                .filter(MigrationIssue::isBlocking)
                .count();

        migration.setUnresolvedIssueCount((int) blocking);
        migration.setStatus(blocking > 0
                ? AssistedMigrationStatus.REVIEW_REQUIRED
                : AssistedMigrationStatus.READY);

        return migrationRepository.save(migration);
    }

    /** One decision, applied to every row that raised the same question. */
    @Transactional
    public MigrationIssue resolve(
            UUID businessId,
            UUID migrationId,
            UUID issueId,
            Map<String, Object> resolution
    ) {
        findOwned(businessId, migrationId);

        MigrationIssue issue = issueRepository.findByIdAndMigrationId(issueId, migrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That issue has not been found"));

        issue.setResolution(resolution == null ? Map.of() : resolution);
        issue.setStatus(MigrationIssueStatus.RESOLVED);

        return issueRepository.save(issue);
    }

    public List<MigrationIssue> findIssues(UUID businessId, UUID migrationId) {
        findOwned(businessId, migrationId);

        return issueRepository.findByMigrationIdOrderBySeverityDescAffectedRowsDesc(migrationId);
    }

    // --- handing over --------------------------------------------------------------

    /**
     * Turns the prepared data into an ordinary import job.
     *
     * The end of assisted migration's authority. What it produces is one of
     * FluxiBiz's own workbooks, uploaded through the same door a shopkeeper
     * uses — so checking, review and commit are the ones every shop already
     * has, and there is no second path into a catalogue.
     *
     * Idempotent: a migration that already prepared an import returns that one
     * rather than making a second.
     */
    @Transactional
    public ImportJobResponse prepareImport(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        if (migration.getPreparedImportJobId() != null) {
            return importJobService.findJobAsOperator(businessId, migration.getPreparedImportJobId());
        }

        requireMapping(migration);
        requireNothingBlocking(migrationId);

        migration.setStatus(AssistedMigrationStatus.PREPARING_IMPORT);

        TransformResult result = transformRows(migration);

        if (result.rows().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "There is nothing in this file to import.");
        }

        byte[] workbook = buildWorkbook(migration, result);

        ImportJobResponse job = importJobService.uploadAsOperator(
                businessId,
                migration.getTargetImportType(),
                new PreparedMultipartFile(preparedFileName(migration, result), workbook));

        /*
         * The columns are ours, so the mapping is not a guess — the workbook was
         * written with FluxiBiz's own headings and the importer recognises every
         * one. Saving it here means the operator lands on checking rather than
         * on a matching screen with nothing to decide.
         */
        importJobService.saveMappingAsOperator(businessId, job.id(), new ImportMappingRequest(
                selfMapping(migration, result),
                ImportDuplicateStrategy.SKIP,
                null,
                null));

        migration.setPreparedImportJobId(job.id());
        migration.setStatus(AssistedMigrationStatus.IMPORT_PREPARED);
        migrationRepository.save(migration);

        return importJobService.findJobAsOperator(businessId, job.id());
    }

    /**
     * Records what each of the customer's records became here.
     *
     * Run after the prepared import has been brought in, because only then do
     * the FluxiBiz ids exist. Useless today and the reason a delta migration is
     * possible tomorrow: when the shop says "P001 changed", the honest answer
     * comes from having written this down at the moment we knew, rather than
     * from matching on names a year later.
     *
     * The SKU is the bridge — it is the customer's own identifier, it survived
     * the transform unchanged, and the importer matched on it. A row without one
     * is skipped rather than linked on a guess.
     */
    @Transactional
    public int linkImportedEntities(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        if (migration.getPreparedImportJobId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This migration has not produced an import yet.");
        }

        List<ImportRow> rows = importRowRepository.findByImportJobIdAndStatusInOrderByRowNumberAsc(
                migration.getPreparedImportJobId(),
                List.of(ImportRowStatus.CREATED, ImportRowStatus.UPDATED));

        List<MigrationEntityLink> links = new ArrayList<>();

        for (ImportRow row : rows) {
            Object sku = row.getNormalizedData().get("sku");

            if (sku == null || row.getCommittedEntityId() == null) {
                continue;
            }

            String sourceId = sku.toString();

            if (linkRepository
                    .findByBusinessIdAndSourceSystemAndSourceEntityTypeAndSourceEntityId(
                            businessId, sourceSystemOf(migration), "PRODUCT", sourceId)
                    .isPresent()) {
                continue;
            }

            MigrationEntityLink link = new MigrationEntityLink();

            link.setBusinessId(businessId);
            link.setMigrationId(migrationId);
            link.setSourceSystem(sourceSystemOf(migration));
            link.setSourceEntityType("PRODUCT");
            link.setSourceEntityId(sourceId);
            link.setFluxibizEntityType(MigrationEntityType.ITEM);
            link.setFluxibizEntityId(row.getCommittedEntityId());

            links.add(link);
        }

        linkRepository.saveAll(links);
        migration.setStatus(AssistedMigrationStatus.COMPLETED);
        migrationRepository.save(migration);

        return links.size();
    }

    /**
     * Whose system this came from.
     *
     * The file name is a poor name for a system and the only one we have — the
     * customer's export does not say what wrote it. Recorded as given so that a
     * later migration from the same source can be told apart from a different
     * one, rather than left blank and ambiguous.
     */
    private String sourceSystemOf(AssistedMigration migration) {
        return migration.getSourceFileName() == null ? "UNKNOWN" : migration.getSourceFileName();
    }

    /** The prepared workbook itself, for an operator who wants to see it. */
    public byte[] preparedWorkbook(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        requireMapping(migration);

        TransformResult result = transformRows(migration);

        return buildWorkbook(migration, result);
    }

    // --- shared --------------------------------------------------------------------

    /**
     * What the shop already has, read once for the whole file.
     *
     * One query rather than one per row: a fifteen thousand row file against a
     * two thousand item catalogue should cost a single read, and asking per row
     * is the difference between a migration that prepares in seconds and one
     * that takes an afternoon.
     */
    private DuplicateDetectionService.ExistingCatalogue catalogueOf(UUID businessId) {
        List<Item> items = itemRepository.findAllByBusinessIdOrderByNameAsc(businessId);

        return DuplicateDetectionService.ExistingCatalogue.of(
                items.stream().map(Item::getSku).toList(),
                items.stream().map(Item::getName).toList());
    }

    private byte[] buildWorkbook(AssistedMigration migration, TransformResult result) {
        ImportSample shape = workbookWriter.shapeFor(migration.getTargetImportType(), result.rows());

        return workbookWriter.write(shape, result.rows(), result.units());
    }

    private String preparedFileName(AssistedMigration migration, TransformResult result) {
        return workbookWriter.shapeFor(migration.getTargetImportType(), result.rows()).getFileName();
    }

    /**
     * Every column of the workbook we just wrote, pointed at its own field.
     *
     * The headings are the fields' labels, so this is a restatement rather than
     * a decision — but stating it saves the operator a screen.
     */
    private Map<String, String> selfMapping(AssistedMigration migration, TransformResult result) {
        ImportSample shape = workbookWriter.shapeFor(migration.getTargetImportType(), result.rows());
        Map<String, String> mapping = new LinkedHashMap<>();

        shape.getColumns().forEach(field -> mapping.put(field.getLabel(), field.name()));

        return mapping;
    }

    private TransformResult transformRows(AssistedMigration migration) {
        Map<String, ImportField> mapping = new LinkedHashMap<>();

        migration.getColumnMappings().forEach((column, field) -> {
            try {
                mapping.put(column, ImportField.valueOf(field));
            } catch (IllegalArgumentException e) {
                log.warn("Migration {} maps {} to unknown field {}", migration.getId(), column, field);
            }
        });

        TransformResult result = transformer.transform(
                readRows(migration),
                mapping,
                migration.getTargetImportType(),
                decisionsOf(migration.getId()),
                migration.getOptionAxisNames());

        /*
         * Duplicates and category spellings are questions about the file as a
         * whole rather than about any row in it, so they are asked once the
         * rows exist rather than while they are being read.
         */
        List<TransformResult.Finding> findings = new ArrayList<>(result.findings());

        findings.addAll(duplicates.findWithin(result.rows()));
        findings.addAll(duplicates.findAgainstCatalogue(
                result.rows(), catalogueOf(migration.getBusiness().getId())));
        findings.addAll(categories.find(result.rows()));

        return new TransformResult(result.rows(), findings, result.units());
    }

    /**
     * The operator's answers, in the shape the transformer looks them up by.
     *
     * A resolved unknown unit becomes a real unit declaration, which is what
     * lets the same file transform again without asking, and what puts the unit
     * on the workbook's Units sheet for the importer to create.
     */
    private Map<String, DeclaredUnit> decisionsOf(UUID migrationId) {
        Map<String, DeclaredUnit> decisions = new LinkedHashMap<>();

        for (MigrationIssue issue : issueRepository.findByMigrationIdAndStatusNot(
                migrationId, MigrationIssueStatus.OPEN)) {

            if (!"UNIT_UNKNOWN".equals(issue.getCode()) || issue.getResolution().isEmpty()) {
                continue;
            }

            Object name = issue.getResolution().get("name");
            Object symbol = issue.getResolution().get("symbol");
            Object category = issue.getResolution().get("category");

            if (name == null || category == null) {
                continue;
            }

            try {
                decisions.put(
                        ImportField.UNIT.name() + "|" + issue.getSourceValue().toLowerCase(),
                        new DeclaredUnit(
                                name.toString(),
                                symbol == null ? null : symbol.toString(),
                                UnitCategory.valueOf(category.toString().toUpperCase()),
                                "From " + issue.getSourceValue()));
            } catch (IllegalArgumentException e) {
                log.warn("Migration {} has an issue resolved to an unknown unit type", migrationId);
            }
        }

        return decisions;
    }

    /**
     * Files the run's findings as one issue per question.
     *
     * Grouped by the field and the source value that caused them, because that
     * is the unit an operator can actually decide: "SACK" is one decision
     * covering four hundred and fifty rows, not four hundred and fifty.
     */
    private void record(AssistedMigration migration, List<TransformResult.Finding> findings) {
        Map<String, List<TransformResult.Finding>> grouped = new LinkedHashMap<>();

        for (TransformResult.Finding finding : findings) {
            grouped.computeIfAbsent(
                    finding.code() + "|" + finding.targetField() + "|" + finding.sourceValue(),
                    key -> new ArrayList<>()).add(finding);
        }

        List<MigrationIssue> issues = new ArrayList<>();

        for (List<TransformResult.Finding> group : grouped.values()) {
            TransformResult.Finding first = group.getFirst();

            if (isAlreadyDecided(migration.getId(), first)) {
                continue;
            }

            MigrationIssue issue = new MigrationIssue();

            issue.setMigration(migration);
            issue.setCode(first.code());
            issue.setTargetField(first.targetField());
            issue.setSourceValue(first.sourceValue());
            issue.setMessage(first.message());
            issue.setSeverity(severityOf(first));
            issue.setAffectedRows(group.size());
            issue.setSampleRows(group.stream()
                    .map(TransformResult.Finding::rowNumber)
                    .sorted()
                    .limit(5)
                    .toList());
            issue.setSuggestion(suggestionFor(first));

            issues.add(issue);
        }

        issues.sort(Comparator.comparing((MigrationIssue i) -> i.getSeverity().ordinal()).reversed());
        issueRepository.saveAll(issues);
    }

    private boolean isAlreadyDecided(UUID migrationId, TransformResult.Finding finding) {
        return issueRepository.findByMigrationIdAndStatusNot(migrationId, MigrationIssueStatus.OPEN)
                .stream()
                .anyMatch(issue -> issue.getCode().equals(finding.code())
                        && issue.getSourceValue().equals(finding.sourceValue()));
    }

    /**
     * How loudly to say it.
     *
     * The distinction that makes the review screen readable: what we already
     * did, what we would do given a nod, what merely deserves a look, and what
     * actually stops the import.
     */
    private MigrationIssueSeverity severityOf(TransformResult.Finding finding) {
        return switch (finding.code()) {
            case "VALUE_NORMALIZED", "UNIT_NORMALIZED" -> MigrationIssueSeverity.AUTO_FIXED;
            case "CATEGORY_SPELLINGS" -> MigrationIssueSeverity.SUGGESTION;
            case "POSSIBLE_DUPLICATE", "NAME_ALREADY_IN_CATALOGUE" -> MigrationIssueSeverity.WARNING;
            case "ALREADY_IN_CATALOGUE" -> MigrationIssueSeverity.INFO;
            case "NAME_MISSING" -> MigrationIssueSeverity.ERROR;
            default -> finding.blocking()
                    ? MigrationIssueSeverity.REVIEW_REQUIRED
                    : MigrationIssueSeverity.INFO;
        };
    }

    /** What we would do, offered as a starting point rather than applied. */
    private Map<String, Object> suggestionFor(TransformResult.Finding finding) {
        if (!"UNIT_UNKNOWN".equals(finding.code())) {
            return Map.of();
        }

        return new LinkedHashMap<>(Map.of(
                "name", finding.sourceValue(),
                "symbol", finding.sourceValue().toLowerCase(),
                "categories", UnitCategory.values()));
    }

    private void requireMapping(AssistedMigration migration) {
        if (migration.getColumnMappings().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Match the columns before going on.");
        }
    }

    private void requireNothingBlocking(UUID migrationId) {
        long blocking = issueRepository.findByMigrationIdOrderBySeverityDescAffectedRowsDesc(migrationId)
                .stream()
                .filter(MigrationIssue::isBlocking)
                .count();

        if (blocking > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    blocking + " thing" + (blocking == 1 ? "" : "s") + " still need a decision"
                            + " before this can become an import.");
        }
    }

    private List<SourceRow> readRows(AssistedMigration migration) {
        if (migration.getRawObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This migration has no file yet.");
        }

        SourceFileParser parser = parsers.parserFor(migration.getSourceFileName());
        List<SourceRow> rows = new ArrayList<>();

        try (InputStream input = minioService.readImportFile(migration.getRawObjectKey())) {
            parser.readRows(input, MAX_ROWS, rows::add);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "That file could not be read.", e);
        }

        return rows;
    }

    private SourceFileParser.SourceHeader readHeader(SourceFileParser parser, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return parser.readHeader(input, SAMPLE_ROWS);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "That file could not be read.", e);
        }
    }

    public AssistedMigration findOwned(UUID businessId, UUID migrationId) {
        businessHelper.findBusinessForOperator(businessId);

        return migrationRepository.findByIdAndBusinessId(migrationId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That migration has not been found"));
    }
}
