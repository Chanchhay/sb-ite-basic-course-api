package kh.edu.istad.ite.features.migration.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportMappingRequest;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportSample;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParserRegistry;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.features.dataimport.service.ImportJobService;
import kh.edu.istad.ite.features.migration.dto.AssistedMigrationDtos;
import kh.edu.istad.ite.features.migration.duplicate.DuplicateDetectionService;
import kh.edu.istad.ite.features.migration.entity.AssistedMigration;
import kh.edu.istad.ite.features.migration.entity.AssistedMigrationSource;
import kh.edu.istad.ite.features.migration.entity.MigrationEntityLink;
import kh.edu.istad.ite.features.migration.entity.MigrationIssue;
import kh.edu.istad.ite.features.migration.entity.MigrationSourceRelationship;
import kh.edu.istad.ite.features.migration.join.JoinAnalysisService;
import kh.edu.istad.ite.features.migration.join.JoinQuality;
import kh.edu.istad.ite.features.migration.join.JoinSuggestion;
import kh.edu.istad.ite.features.migration.join.JoinSuggestionService;
import kh.edu.istad.ite.features.migration.join.SourceJoiner;
import kh.edu.istad.ite.features.migration.mapping.ColumnMappingService;
import kh.edu.istad.ite.features.migration.mapping.ColumnSuggestion;
import kh.edu.istad.ite.features.migration.normalize.CategoryGrouping;
import kh.edu.istad.ite.features.migration.profile.SourceProfilingService;
import kh.edu.istad.ite.features.migration.repository.AssistedMigrationRepository;
import kh.edu.istad.ite.features.migration.repository.AssistedMigrationSourceRepository;
import kh.edu.istad.ite.features.migration.repository.MigrationEntityLinkRepository;
import kh.edu.istad.ite.features.migration.repository.MigrationIssueRepository;
import kh.edu.istad.ite.features.migration.repository.MigrationSourceRelationshipRepository;
import kh.edu.istad.ite.features.migration.resolve.FieldRule;
import kh.edu.istad.ite.features.migration.resolve.FieldValue;
import kh.edu.istad.ite.features.migration.resolve.MigrationFieldPolicy;
import kh.edu.istad.ite.features.migration.resolve.MissingFieldReport;
import kh.edu.istad.ite.features.migration.resolve.MissingFieldResolutionService;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.PreparedWorkbookWriter;
import kh.edu.istad.ite.features.migration.transform.SourceTransformer;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.AssistedMigrationStatus;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.MigrationEntityType;
import kh.edu.istad.ite.shared.enums.MigrationIssueSeverity;
import kh.edu.istad.ite.shared.enums.MigrationIssueStatus;
import kh.edu.istad.ite.shared.enums.MigrationJoinType;
import kh.edu.istad.ite.shared.enums.MigrationSourcePurpose;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a customer's own exports through to a prepared FluxiBiz import.
 *
 * The order of operations is the feature: take the files, describe each one,
 * guess what its columns are, work out how they relate to each other, let an
 * operator correct all of that, turn it into FluxiBiz's words, put what remains
 * ambiguous to a person once, and only then produce an import job. Nothing
 * here writes to a catalogue — the last step hands an ordinary workbook to the
 * ordinary importer, which checks and commits it the way it does for every
 * shop.
 *
 * Several files rather than one, because that is what customers actually have.
 * The product list, the stock count and the price sheet each hold part of the
 * answer, and joining them is the work we are doing on their behalf. What is
 * still missing after the join is a real gap, and gets asked about rather than
 * filled in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistedMigrationService {

    /** More than any real export, and small enough to hold while transforming. */
    private static final int MAX_ROWS = 20_000;
    private static final int SAMPLE_ROWS = 20;

    /** Enough files for a catalogue, a stock count, prices and a category list. */
    private static final int MAX_SOURCES = 6;

    private final AssistedMigrationRepository migrationRepository;
    private final AssistedMigrationSourceRepository sourceRepository;
    private final MigrationSourceRelationshipRepository relationshipRepository;
    private final MigrationIssueRepository issueRepository;
    private final SourceFileParserRegistry parsers;
    private final SourceProfilingService profiler;
    private final ColumnMappingService columnMapper;
    private final SourceTransformer transformer;
    private final SourceJoiner joiner;
    private final JoinSuggestionService joinSuggestions;
    private final JoinAnalysisService joinAnalysis;
    private final MissingFieldResolutionService missingFields;
    private final PreparedWorkbookWriter workbookWriter;
    private final ImportJobService importJobService;
    private final MinioService minioService;
    private final BusinessHelper businessHelper;
    private final MigrationEntityLinkRepository linkRepository;
    private final ImportRowRepository importRowRepository;
    private final DuplicateDetectionService duplicates;
    private final CategoryGrouping categories;
    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final UnitRepository unitRepository;

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
     * Adds one of the customer's files, stored exactly as it arrived.
     *
     * Raw files are never rewritten. Everything this feature works out lives
     * beside them, so an operator who suspects the pipeline misread something
     * can always go back to what the customer actually sent.
     *
     * The first file added is the one whose rows become items; the rest fill in
     * what it left out. That is positional rather than a flag so there is
     * always exactly one, which a flag could not promise.
     */
    @Transactional
    public AssistedMigrationSource addSource(
            UUID businessId,
            UUID migrationId,
            MultipartFile file,
            MigrationSourcePurpose purpose
    ) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        requireNotHandedOver(migration);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to migrate.");
        }

        long existing = sourceRepository.countByMigrationId(migrationId);

        if (existing >= MAX_SOURCES) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A migration holds at most " + MAX_SOURCES + " files. Remove one to add another.");
        }

        SourceFileParser parser = parsers.parserFor(file.getOriginalFilename());
        SourceFileParser.SourceHeader header = readHeader(parser, file);

        AssistedMigrationSource source = new AssistedMigrationSource();

        source.setMigration(migration);
        source.setOrdinal((int) existing);
        source.setSourceType(parser.sourceType());
        source.setFileName(file.getOriginalFilename());
        source.setFileSize(file.getSize());
        source.setSourceColumns(header.columns());
        source.setColumnCount(header.columns().size());
        source.setRawObjectKey(minioService.uploadImportFile(file, businessId));
        source.setPurpose(purpose == null ? SourcePurposeGuess.from(header.columns()) : purpose);

        sourceRepository.save(source);

        migration.setStatus(AssistedMigrationStatus.UPLOADED);
        mirrorPrimaryOntoMigration(migration);

        return source;
    }

    /**
     * The older single-file entry point, kept because nothing needs breaking.
     *
     * A migration with one file is a migration with one source, so this adds
     * one and lets everything downstream treat it identically. Two paths would
     * eventually disagree about the file that used only one of them.
     */
    @Transactional
    public AssistedMigration attachFile(UUID businessId, UUID migrationId, MultipartFile file) {
        addSource(businessId, migrationId, file, null);

        return findOwned(businessId, migrationId);
    }

    public List<AssistedMigrationSource> findSources(UUID businessId, UUID migrationId) {
        findOwned(businessId, migrationId);

        return sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);
    }

    /**
     * Drops a file, and any join that depended on it.
     *
     * A relationship pointing at a file that is no longer there would either
     * fail at transform time or, worse, be silently skipped — leaving a
     * migration that looks joined and is not.
     */
    @Transactional
    public void removeSource(UUID businessId, UUID migrationId, UUID sourceId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        requireNotHandedOver(migration);

        AssistedMigrationSource source = sourceRepository
                .findByIdAndMigrationId(sourceId, migrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That file has not been found"));

        relationshipRepository.deleteByMigrationIdAndSource(migrationId, sourceId);
        sourceRepository.delete(source);
        sourceRepository.flush();

        /*
         * Positions close up, so the first file is always the main one. Left
         * with a gap, removing the original main file would leave a migration
         * with no source whose rows become items.
         */
        List<AssistedMigrationSource> remaining =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);

        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setOrdinal(index);
        }

        sourceRepository.saveAll(remaining);
        mirrorPrimaryOntoMigration(migration);
    }

    @Transactional
    public AssistedMigrationSource setSourcePurpose(
            UUID businessId,
            UUID migrationId,
            UUID sourceId,
            MigrationSourcePurpose purpose
    ) {
        findOwned(businessId, migrationId);

        AssistedMigrationSource source = sourceRepository
                .findByIdAndMigrationId(sourceId, migrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That file has not been found"));

        source.setPurpose(purpose == null ? MigrationSourcePurpose.UNKNOWN : purpose);

        return sourceRepository.save(source);
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

    /** What every column of one file contains, and what each one probably is. */
    public record Analysis(
            UUID sourceId,
            String fileName,
            MigrationSourcePurpose purpose,
            SourceProfilingService.SourceProfile profile,
            List<ColumnSuggestion> suggestions
    ) {
    }

    /**
     * Reads and describes every file the migration holds.
     *
     * All of them at once rather than one at a time, because an operator
     * uploads what the customer sent and then wants to see it — asking them to
     * press analyse against each file in turn is ceremony, not control.
     */
    @Transactional
    public List<Analysis> analyzeAll(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);
        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);

        requireSources(sources);

        List<Analysis> analyses = new ArrayList<>();

        for (AssistedMigrationSource source : sources) {
            analyses.add(analyzeOne(migration, source));
        }

        migration.setStatus(AssistedMigrationStatus.ANALYZED);
        mirrorPrimaryOntoMigration(migration);

        return analyses;
    }

    /** The main file's analysis, for callers that only know about one. */
    @Transactional
    public Analysis analyze(UUID businessId, UUID migrationId) {
        List<Analysis> analyses = analyzeAll(businessId, migrationId);

        return analyses.getFirst();
    }

    private Analysis analyzeOne(AssistedMigration migration, AssistedMigrationSource source) {
        List<SourceRow> rows = readRows(source);

        SourceProfilingService.SourceProfile profile =
                profiler.profile(source.getSourceColumns(), rows);

        List<ColumnSuggestion> suggestions =
                columnMapper.suggest(profile.columns(), migration.getTargetImportType());

        source.setRowCount(profile.rows());
        source.setAnalyzed(true);

        /*
         * Only the confident ones are filled in. A suggestion the operator has
         * to check is worth showing and not worth pre-selecting: accepting a
         * screen of amber ticks is exactly how a column gets mapped wrongly.
         */
        if (source.getColumnMappings().isEmpty()) {
            Map<String, String> preselected = new LinkedHashMap<>();

            suggestions.stream()
                    .filter(ColumnSuggestion::isHigh)
                    .forEach(s -> preselected.put(s.sourceColumn(), s.target().name()));

            source.setColumnMappings(preselected);
        }

        sourceRepository.save(source);

        return new Analysis(
                source.getId(), source.getFileName(), source.getPurpose(), profile, suggestions);
    }

    /** One file's headings, pointed at FluxiBiz fields. */
    @Transactional
    public AssistedMigrationSource saveSourceMapping(
            UUID businessId,
            UUID migrationId,
            UUID sourceId,
            Map<String, String> mappings
    ) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        requireNotHandedOver(migration);

        AssistedMigrationSource source = sourceRepository
                .findByIdAndMigrationId(sourceId, migrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That file has not been found"));

        source.setColumnMappings(cleaned(mappings));
        sourceRepository.save(source);

        migration.setStatus(AssistedMigrationStatus.ANALYZED);
        mirrorPrimaryOntoMigration(migration);

        return source;
    }

    /** The main file's mapping, plus what the option axes are called. */
    @Transactional
    public AssistedMigration saveMapping(
            UUID businessId,
            UUID migrationId,
            Map<String, String> mappings,
            Map<String, String> optionAxisNames
    ) {
        AssistedMigration migration = findOwned(businessId, migrationId);
        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);

        requireSources(sources);

        if (optionAxisNames != null) {
            migration.setOptionAxisNames(new LinkedHashMap<>(optionAxisNames));
        }

        AssistedMigrationSource primary = sources.getFirst();

        primary.setColumnMappings(cleaned(mappings));
        sourceRepository.save(primary);

        migration.setStatus(AssistedMigrationStatus.ANALYZED);
        mirrorPrimaryOntoMigration(migration);

        return migrationRepository.save(migration);
    }

    private Map<String, String> cleaned(Map<String, String> mappings) {
        Map<String, String> kept = new LinkedHashMap<>();

        if (mappings != null) {
            mappings.forEach((column, field) -> {
                if (field != null && !field.isBlank()) {
                    kept.put(column, field);
                }
            });
        }

        return kept;
    }

    // --- connecting ----------------------------------------------------------------

    /** Which columns of the other files look like they identify the same records. */
    public List<JoinSuggestion> suggestJoins(UUID businessId, UUID migrationId) {
        findOwned(businessId, migrationId);

        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);

        if (sources.size() < 2) {
            return List.of();
        }

        Map<UUID, List<SourceRow>> rows = readAll(sources);

        return joinSuggestions.suggest(
                sources.getFirst(), sources.subList(1, sources.size()), rows);
    }

    /**
     * Replaces the migration's joins with the set an operator approved.
     *
     * All at once, because the joins only make sense together — half-applying
     * a change would leave a migration whose files are connected in a way
     * nobody chose. Each is measured before it is accepted, and a join that
     * would multiply rows is refused outright rather than warned about: there
     * is no honest way to carry it out, so agreeing to it cannot be an option.
     */
    @Transactional
    public List<MigrationSourceRelationship> saveRelationships(
            UUID businessId,
            UUID migrationId,
            List<AssistedMigrationDtos.RelationshipRequest> requests
    ) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        requireNotHandedOver(migration);

        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);

        requireSources(sources);

        Map<UUID, AssistedMigrationSource> byId = new LinkedHashMap<>();
        sources.forEach(source -> byId.put(source.getId(), source));

        Map<UUID, List<SourceRow>> rows = readAll(sources);
        List<MigrationSourceRelationship> saved = new ArrayList<>();

        /*
         * Clearing the old set flushes and empties the persistence context,
         * which leaves the migration loaded above detached. The new rows are
         * pointed at a fresh reference rather than that one, so the foreign key
         * is written from something Hibernate is still managing.
         */
        relationshipRepository.deleteByMigrationId(migrationId);

        AssistedMigration owner = migrationRepository.getReferenceById(migrationId);

        for (AssistedMigrationDtos.RelationshipRequest request : requests == null ? List.<AssistedMigrationDtos.RelationshipRequest>of() : requests) {
            AssistedMigrationSource left = byId.get(request.leftSourceId());
            AssistedMigrationSource right = byId.get(request.rightSourceId());

            if (left == null || right == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "That join names a file this migration does not have.");
            }

            JoinQuality quality = joinAnalysis.analyse(
                    rows.getOrDefault(left.getId(), List.of()), request.leftColumn(),
                    rows.getOrDefault(right.getId(), List.of()), request.rightColumn());

            if (!quality.isUsable()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + request.leftColumn() + "\" and \"" + request.rightColumn()
                                + "\" both repeat, so this join would multiply rows rather than"
                                + " match them. Choose columns that identify one record.");
            }

            MigrationSourceRelationship relationship = new MigrationSourceRelationship();

            relationship.setMigration(owner);
            relationship.setLeftSourceId(left.getId());
            relationship.setLeftColumn(request.leftColumn());
            relationship.setRightSourceId(right.getId());
            relationship.setRightColumn(request.rightColumn());
            relationship.setJoinType(request.joinType() == null
                    ? MigrationJoinType.LEFT
                    : request.joinType());

            saved.add(relationship);
        }

        return relationshipRepository.saveAll(saved);
    }

    public List<MigrationSourceRelationship> findRelationships(UUID businessId, UUID migrationId) {
        findOwned(businessId, migrationId);

        return relationshipRepository.findByMigrationId(migrationId);
    }

    /** What the joins as saved actually do, counted against the files. */
    public List<AssistedMigrationDtos.JoinQualityResponse> joinQuality(
            UUID businessId,
            UUID migrationId
    ) {
        findOwned(businessId, migrationId);

        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);
        List<MigrationSourceRelationship> relationships =
                relationshipRepository.findByMigrationId(migrationId);

        if (sources.isEmpty() || relationships.isEmpty()) {
            return List.of();
        }

        Map<UUID, AssistedMigrationSource> byId = new LinkedHashMap<>();
        sources.forEach(source -> byId.put(source.getId(), source));

        Map<UUID, List<SourceRow>> rows = readAll(sources);
        List<AssistedMigrationDtos.JoinQualityResponse> report = new ArrayList<>();

        for (MigrationSourceRelationship relationship : relationships) {
            AssistedMigrationSource left = byId.get(relationship.getLeftSourceId());
            AssistedMigrationSource right = byId.get(relationship.getRightSourceId());

            if (left == null || right == null) {
                continue;
            }

            JoinQuality quality = joinAnalysis.analyse(
                    rows.getOrDefault(left.getId(), List.of()), relationship.getLeftColumn(),
                    rows.getOrDefault(right.getId(), List.of()), relationship.getRightColumn());

            report.add(new AssistedMigrationDtos.JoinQualityResponse(
                    relationship.getId(),
                    left.getId(), left.getFileName(), relationship.getLeftColumn(),
                    right.getId(), right.getFileName(), relationship.getRightColumn(),
                    relationship.getJoinType(),
                    quality.leftRows(), quality.rightRows(),
                    quality.matchedLeftRows(), quality.unmatchedLeftRows(),
                    quality.unmatchedRightRows(),
                    quality.duplicateLeftKeys(), quality.duplicateRightKeys(),
                    quality.cardinality(), quality.isUsable()));
        }

        return report;
    }

    // --- transforming --------------------------------------------------------------

    /**
     * Reads every file into FluxiBiz's terms and files what is left over.
     *
     * Safe to run again. Findings from the last run are cleared and re-derived;
     * decisions an operator already made are kept and reused, so correcting one
     * column does not cost them forty answers.
     */
    @Transactional
    public AssistedMigration transform(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        migration.setStatus(AssistedMigrationStatus.TRANSFORMING);

        Prepared prepared = prepare(migration);

        issueRepository.deleteByMigrationIdAndStatus(migrationId, MigrationIssueStatus.OPEN);
        record(migration, prepared.findings());

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

    /** Every field that matters, and how much of it the files actually supplied. */
    public MissingFieldReport missingFields(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        return prepare(migration).missing();
    }

    /**
     * Why one record came out the way it did.
     *
     * Answered by running the migration again for the whole file and reading
     * off one row, which is affordable because the whole pipeline is
     * deterministic and bounded. Storing a provenance record per field per row
     * would mean hundreds of thousands of rows per migration, rewritten every
     * time an operator changed one mapping, to answer a question asked a
     * handful of times.
     */
    public AssistedMigrationDtos.RowExplanation explainRow(
            UUID businessId,
            UUID migrationId,
            int rowNumber
    ) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        PreparedRow row = prepare(migration).rows().stream()
                .filter(candidate -> candidate.sourceRowNumber() == rowNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That row is not in this migration."));

        List<AssistedMigrationDtos.FieldExplanation> fields = new ArrayList<>();

        row.values().forEach((field, value) -> {
            FieldValue origin = row.originOf(field);

            fields.add(new AssistedMigrationDtos.FieldExplanation(
                    field.name(),
                    field.getLabel(),
                    value,
                    origin == null ? FieldResolutionSource.DIRECT_SOURCE : origin.resolution(),
                    origin == null ? null : origin.sourceFile(),
                    origin == null ? null : origin.sourceColumn(),
                    origin == null ? null : origin.sourceRow(),
                    origin == null ? null : origin.value(),
                    origin == null ? null : origin.rule()));
        });

        return new AssistedMigrationDtos.RowExplanation(
                rowNumber, row.get(ImportField.NAME), fields);
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

        requireNothingBlocking(migrationId);

        migration.setStatus(AssistedMigrationStatus.PREPARING_IMPORT);

        Prepared prepared = prepare(migration);

        if (prepared.rows().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "There is nothing in these files to import.");
        }

        byte[] workbook = buildWorkbook(migration, prepared);

        ImportJobResponse job = importJobService.uploadAsOperator(
                businessId,
                migration.getTargetImportType(),
                new PreparedMultipartFile(preparedFileName(migration, prepared), workbook));

        /*
         * The columns are ours, so the mapping is not a guess — the workbook was
         * written with FluxiBiz's own headings and the importer recognises every
         * one. Saving it here means the operator lands on checking rather than
         * on a matching screen with nothing to decide.
         */
        importJobService.saveMappingAsOperator(businessId, job.id(), new ImportMappingRequest(
                selfMapping(migration, prepared),
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

    /**
     * What handing this over would actually do.
     *
     * Counted against the shop's catalogue rather than against the files:
     * "eight categories will be created" and "eight categories are named" are
     * different numbers, and only the first is news to whoever is about to
     * agree to it.
     */
    public AssistedMigrationDtos.PreparedSummary summarise(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);
        Prepared prepared = prepare(migration);

        List<String> unitsToCreate = new ArrayList<>();
        int unitsExisting = 0;

        for (DeclaredUnit unit : prepared.units()) {
            if (unitRepository.findSelectableUnitsNamed(businessId, unit.name()).isEmpty()) {
                unitsToCreate.add(unit.label());
            } else {
                unitsExisting++;
            }
        }

        Set<String> namedCategories = new LinkedHashSet<>();

        prepared.rows().forEach(row -> {
            addIfPresent(namedCategories, row.get(ImportField.ITEM_GROUP));
            addIfPresent(namedCategories, row.get(ImportField.PARENT_GROUP));
        });

        List<String> categoriesToCreate = new ArrayList<>();
        int categoriesExisting = 0;

        for (String category : namedCategories) {
            if (itemGroupRepository.findFirstByBusinessIdAndNameIgnoreCase(businessId, category)
                    .isPresent()) {
                categoriesExisting++;
            } else {
                categoriesToCreate.add(category);
            }
        }

        /*
         * Items, not rows. A file listing one row per option describes one
         * shirt across five of them, and promising five items would be a
         * number the operator only discovers is wrong afterwards.
         */
        Set<String> distinctItems = new LinkedHashSet<>();

        prepared.rows().forEach(row -> {
            String key = row.get(ImportField.OPTION_GROUP_KEY);
            distinctItems.add(key != null ? "g:" + key : "r:" + row.sourceRowNumber());
        });

        long optionRows = prepared.rows().stream()
                .filter(row -> row.get(ImportField.OPTION_GROUP_KEY) != null)
                .count();

        List<MigrationIssue> issues =
                issueRepository.findByMigrationIdOrderBySeverityDescAffectedRowsDesc(migrationId);

        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migrationId);

        return new AssistedMigrationDtos.PreparedSummary(
                distinctItems.size(),
                (int) optionRows,
                unitsExisting,
                unitsToCreate.size(),
                categoriesExisting,
                categoriesToCreate.size(),
                countOf(issues, "POSSIBLE_DUPLICATE"),
                countOf(issues, "ALREADY_IN_CATALOGUE"),
                (int) issues.stream().filter(MigrationIssue::isBlocking).count(),
                List.copyOf(unitsToCreate),
                List.copyOf(categoriesToCreate),
                sources.stream().map(AssistedMigrationSource::getFileName).toList(),
                prepared.missing().resolvedBy());
    }

    private int countOf(List<MigrationIssue> issues, String code) {
        return (int) issues.stream().filter(issue -> issue.getCode().equals(code)).count();
    }

    private void addIfPresent(Set<String> into, String value) {
        if (value != null && !value.isBlank()) {
            into.add(value.trim());
        }
    }

    /** The prepared workbook itself, for an operator who wants to see it. */
    public byte[] preparedWorkbook(UUID businessId, UUID migrationId) {
        AssistedMigration migration = findOwned(businessId, migrationId);

        return buildWorkbook(migration, prepare(migration));
    }

    // --- shared --------------------------------------------------------------------

    /**
     * Everything one reading of a migration produces.
     *
     * Held together because it is one reading: the rows are what would be
     * imported, the findings are what an operator has to agree to first, the
     * units are what the workbook must declare, and the report is what each
     * field ended up resolving to.
     */
    private record Prepared(
            List<PreparedRow> rows,
            List<TransformResult.Finding> findings,
            List<DeclaredUnit> units,
            MissingFieldReport missing
    ) {
    }

    /**
     * Joins the files, reads them, and fills what it honestly can.
     *
     * The one path. Preview, summary, explanation and handover all run this
     * rather than approximating it, so what an operator is shown is what will
     * actually be handed over — a summary computed a slightly different way is
     * a summary that will eventually be wrong at the worst moment.
     */
    private Prepared prepare(AssistedMigration migration) {
        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migration.getId());

        requireSources(sources);
        requireMapping(sources);

        Map<UUID, List<SourceRow>> rowsBySource = readAll(sources);

        SourceJoiner.JoinedRecords joined = joiner.join(
                sources.getFirst(),
                sources,
                rowsBySource,
                relationshipRepository.findByMigrationId(migration.getId()));

        Decisions decisions = decisionsOf(migration.getId());

        TransformResult result = transformer.transform(
                joined.records(),
                migration.getTargetImportType(),
                decisions.units(),
                migration.getOptionAxisNames());

        MissingFieldResolutionService.Outcome outcome = missingFields.resolve(
                result.rows(), migration.getTargetImportType(), decisions.rules());

        /*
         * Duplicates and category spellings are questions about the file as a
         * whole rather than about any row in it, so they are asked once the
         * rows exist rather than while they are being read.
         */
        List<TransformResult.Finding> findings = new ArrayList<>(result.findings());

        findings.addAll(joined.findings());
        findings.addAll(outcome.findings());
        findings.addAll(duplicates.findWithin(result.rows()));
        findings.addAll(duplicates.findAgainstCatalogue(
                result.rows(), catalogueOf(migration.getBusiness().getId())));
        findings.addAll(categories.find(result.rows()));

        /*
         * A unit chosen for the rows that had none still has to reach the
         * workbook's Units sheet, or the importer would meet a symbol it has
         * never been told how to create.
         */
        List<DeclaredUnit> units = new ArrayList<>(result.units());

        decisions.declaredByRule().forEach(unit -> {
            if (units.stream().noneMatch(known -> known.answersTo(unit.name()))) {
                units.add(unit);
            }
        });

        return new Prepared(result.rows(), findings, units, outcome.report());
    }

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

    private byte[] buildWorkbook(AssistedMigration migration, Prepared prepared) {
        ImportSample shape = workbookWriter.shapeFor(migration.getTargetImportType(), prepared.rows());

        return workbookWriter.write(shape, prepared.rows(), prepared.units());
    }

    private String preparedFileName(AssistedMigration migration, Prepared prepared) {
        return workbookWriter.shapeFor(migration.getTargetImportType(), prepared.rows()).getFileName();
    }

    /**
     * Every column of the workbook we just wrote, pointed at its own field.
     *
     * The headings are the fields' labels, so this is a restatement rather than
     * a decision — but stating it saves the operator a screen.
     */
    private Map<String, String> selfMapping(AssistedMigration migration, Prepared prepared) {
        ImportSample shape = workbookWriter.shapeFor(migration.getTargetImportType(), prepared.rows());
        Map<String, String> mapping = new LinkedHashMap<>();

        shape.getColumns().forEach(field -> mapping.put(field.getLabel(), field.name()));

        return mapping;
    }

    /**
     * Everything an operator has already settled, in the shapes each step wants.
     *
     * @param units         unknown source units, keyed the way the transformer looks them up
     * @param rules         missing-field decisions, narrowest scope first
     * @param declaredByRule the units those rules introduced, for the Units sheet
     */
    private record Decisions(
            Map<String, DeclaredUnit> units,
            List<FieldRule> rules,
            List<DeclaredUnit> declaredByRule
    ) {
    }

    /**
     * Reads the operator's answers back out of the issues that asked them.
     *
     * Kept as issues rather than in a second table of rules. There is one
     * review screen, one place a decision is recorded, and one thing to look at
     * when asking what somebody agreed to — a parallel rules table would mean
     * two, and the two would eventually disagree.
     */
    private Decisions decisionsOf(UUID migrationId) {
        Map<String, DeclaredUnit> units = new LinkedHashMap<>();
        List<FieldRule> rules = new ArrayList<>();
        List<DeclaredUnit> declared = new ArrayList<>();

        for (MigrationIssue issue : issueRepository.findByMigrationIdAndStatusNot(
                migrationId, MigrationIssueStatus.OPEN)) {

            if (issue.getResolution().isEmpty()) {
                continue;
            }

            if ("UNIT_UNKNOWN".equals(issue.getCode())) {
                readUnitDecision(migrationId, issue).ifPresent(unit -> units.put(
                        ImportField.UNIT.name() + "|" + issue.getSourceValue().toLowerCase(), unit));
                continue;
            }

            if ("FIELD_MISSING".equals(issue.getCode())) {
                readFieldRule(migrationId, issue, rules, declared);
            }
        }

        /*
         * Narrowest first, so "services are counted in services" beats
         * "everything is counted in pieces" without an operator having to
         * order their own decisions.
         */
        rules.sort(Comparator.comparingInt(rule -> rule.scope() == FieldRule.Scope.ALL ? 1 : 0));

        return new Decisions(units, rules, declared);
    }

    private java.util.Optional<DeclaredUnit> readUnitDecision(UUID migrationId, MigrationIssue issue) {
        Object name = issue.getResolution().get("name");
        Object symbol = issue.getResolution().get("symbol");
        Object category = issue.getResolution().get("category");

        if (name == null || category == null) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(new DeclaredUnit(
                    name.toString(),
                    symbol == null ? null : symbol.toString(),
                    UnitCategory.valueOf(category.toString().toUpperCase()),
                    "From " + issue.getSourceValue()));
        } catch (IllegalArgumentException e) {
            log.warn("Migration {} has an issue resolved to an unknown unit type", migrationId);
            return java.util.Optional.empty();
        }
    }

    /**
     * One missing-field decision, and the unit it may have introduced.
     *
     * A unit chosen here is a unit the shop does not have yet, so it is
     * declared as well as applied — the value written into the rows is the
     * symbol, and the Units sheet is what tells the importer what that symbol
     * means.
     */
    private void readFieldRule(
            UUID migrationId,
            MigrationIssue issue,
            List<FieldRule> rules,
            List<DeclaredUnit> declared
    ) {
        Object value = issue.getResolution().get("value");

        if (value == null || value.toString().isBlank()) {
            return;
        }

        ImportField field;

        try {
            field = ImportField.valueOf(issue.getTargetField());
        } catch (IllegalArgumentException e) {
            log.warn("Migration {} has a decision about unknown field {}",
                    migrationId, issue.getTargetField());
            return;
        }

        FieldRule.Scope scope = FieldRule.Scope.ALL;
        Object requested = issue.getResolution().get("scope");

        if (requested != null) {
            try {
                scope = FieldRule.Scope.valueOf(requested.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Migration {} has a decision with an unknown scope {}", migrationId, requested);
            }
        }

        Object scopeValue = issue.getResolution().get("scopeValue");

        String written = value.toString().trim();

        if (field == ImportField.UNIT) {
            Object unitName = issue.getResolution().get("name");
            Object unitCategory = issue.getResolution().get("category");

            if (unitName != null && unitCategory != null) {
                try {
                    declared.add(new DeclaredUnit(
                            unitName.toString(),
                            written,
                            UnitCategory.valueOf(unitCategory.toString().toUpperCase()),
                            "Chosen during migration"));
                } catch (IllegalArgumentException e) {
                    log.warn("Migration {} chose a unit with an unknown type", migrationId);
                }
            }
        }

        rules.add(new FieldRule(
                field,
                scope,
                scopeValue == null ? null : scopeValue.toString(),
                written,
                describeRule(field, scope, scopeValue, written)));
    }

    /** The decision as a sentence, so a row can say why it holds what it holds. */
    private String describeRule(
            ImportField field,
            FieldRule.Scope scope,
            Object scopeValue,
            String value
    ) {
        String where = switch (scope) {
            case ALL -> "every row without one";
            case CATEGORY -> "rows in " + scopeValue;
            case ITEM_TYPE -> scopeValue + " rows";
        };

        return "Operator set " + field.getLabel() + " to \"" + value + "\" for " + where;
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
            issue.setSuggestion(suggestionFor(first, migration.getTargetImportType()));

            issues.add(issue);
        }

        issues.sort(Comparator.comparing((MigrationIssue i) -> i.getSeverity().ordinal()).reversed());
        issueRepository.saveAll(issues);
    }

    /**
     * Whether this question has already been answered.
     *
     * Only for questions a single answer settles completely — an unknown unit
     * is "SACK", and once somebody has said what SACK is, it stays said.
     *
     * A missing field is not that. A decision there may be scoped to one
     * category, leaving thousands of rows still unanswered, and suppressing
     * the question because *an* answer exists would let a migration report
     * nothing blocking while rows still have no unit. Those findings are
     * recomputed after every decision is applied, so what survives to here is
     * by definition what remains — and it is asked again, with the smaller
     * number attached.
     */
    private boolean isAlreadyDecided(UUID migrationId, TransformResult.Finding finding) {
        if ("FIELD_MISSING".equals(finding.code())) {
            return false;
        }

        return issueRepository.findByMigrationIdAndStatusNot(migrationId, MigrationIssueStatus.OPEN)
                .stream()
                .anyMatch(issue -> issue.getCode().equals(finding.code())
                        && issue.getSourceValue().equals(finding.sourceValue())
                        && java.util.Objects.equals(issue.getTargetField(), finding.targetField()));
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
            case "SOURCE_NOT_MATCHED" -> MigrationIssueSeverity.WARNING;
            case "NAME_MISSING" -> MigrationIssueSeverity.ERROR;
            default -> finding.blocking()
                    ? MigrationIssueSeverity.REVIEW_REQUIRED
                    : MigrationIssueSeverity.INFO;
        };
    }

    /** What we would do, offered as a starting point rather than applied. */
    private Map<String, Object> suggestionFor(
            TransformResult.Finding finding,
            ImportTargetType targetType
    ) {
        if ("UNIT_UNKNOWN".equals(finding.code())) {
            return new LinkedHashMap<>(Map.of(
                    "name", finding.sourceValue(),
                    "symbol", finding.sourceValue().toLowerCase(),
                    "categories", UnitCategory.values()));
        }

        if (!"FIELD_MISSING".equals(finding.code())) {
            return Map.of();
        }

        ImportField field;

        try {
            field = ImportField.valueOf(finding.targetField());
        } catch (IllegalArgumentException e) {
            return Map.of();
        }

        MigrationFieldPolicy.Policy policy = MigrationFieldPolicy.of(field, targetType);
        Map<String, Object> suggestion = new LinkedHashMap<>();

        suggestion.put("field", field.name());
        suggestion.put("label", field.getLabel());
        suggestion.put("scopes", FieldRule.Scope.values());

        if (policy.suggestion() != null) {
            suggestion.put("value", policy.suggestion());
        }

        if (field == ImportField.UNIT) {
            suggestion.put("categories", UnitCategory.values());
        }

        if (field == ImportField.ITEM_TYPE) {
            suggestion.put("choices", List.of("Physical", "Service", "Digital"));
        }

        if (field == ImportField.TRACK_INVENTORY) {
            suggestion.put("choices", List.of("Yes", "No"));
        }

        return suggestion;
    }

    private void requireSources(List<AssistedMigrationSource> sources) {
        if (sources.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This migration has no files yet.");
        }
    }

    /**
     * The main file has to be matched; the others only if they are joined.
     *
     * A file uploaded and then left alone is harmless — it contributes
     * nothing. A main file with no mapping means nothing can be read at all,
     * which is worth saying plainly rather than discovering as an empty
     * result.
     */
    private void requireMapping(List<AssistedMigrationSource> sources) {
        if (sources.getFirst().getColumnMappings().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Match the columns before going on.");
        }
    }

    private void requireNotHandedOver(AssistedMigration migration) {
        if (migration.getPreparedImportJobId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This migration has already produced an import, so its files cannot change.");
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

    /**
     * The main file's details, copied onto the migration itself.
     *
     * The list screen, the history page and the source-system name all read
     * these, and every one of them predates a migration having more than one
     * file. Keeping them in step costs a few lines here and saves changing
     * three screens to say the same thing a longer way.
     */
    private void mirrorPrimaryOntoMigration(AssistedMigration migration) {
        List<AssistedMigrationSource> sources =
                sourceRepository.findByMigrationIdOrderByOrdinalAsc(migration.getId());

        if (sources.isEmpty()) {
            return;
        }

        AssistedMigrationSource primary = sources.getFirst();

        migration.setSourceType(primary.getSourceType());
        migration.setSourceFileName(primary.getFileName());
        migration.setSourceFileSize(primary.getFileSize());
        migration.setSourceSheet(primary.getSheetName());
        migration.setSourceColumns(primary.getSourceColumns());
        migration.setColumnCount(primary.getColumnCount());
        migration.setRowCount(primary.getRowCount());
        migration.setColumnMappings(primary.getColumnMappings());

        migrationRepository.save(migration);
    }

    /** Every file's rows, each read once. */
    private Map<UUID, List<SourceRow>> readAll(List<AssistedMigrationSource> sources) {
        Map<UUID, List<SourceRow>> rows = new LinkedHashMap<>();

        sources.forEach(source -> rows.put(source.getId(), readRows(source)));

        return rows;
    }

    private List<SourceRow> readRows(AssistedMigrationSource source) {
        if (source.getRawObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That file has not been stored.");
        }

        SourceFileParser parser = parsers.parserFor(source.getFileName());
        List<SourceRow> rows = new ArrayList<>();

        try (InputStream input = minioService.readImportFile(source.getRawObjectKey())) {
            parser.readRows(input, MAX_ROWS, rows::add);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "\"" + source.getFileName() + "\" could not be read.", e);
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
