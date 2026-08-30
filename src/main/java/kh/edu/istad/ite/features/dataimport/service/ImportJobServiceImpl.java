package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.dataimport.dto.ImportColumnsResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportFieldResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportMappingRequest;
import kh.edu.istad.ite.features.dataimport.dto.ImportPreviewResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportReportResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportRowResponse;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.dto.ImportSampleResponse;
import kh.edu.istad.ite.features.dataimport.field.ImportSample;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.canonical.UnitSheetReader;
import kh.edu.istad.ite.features.dataimport.dto.ImportUnitSummary;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.dataimport.field.ImportTemplate;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParserRegistry;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportJobRepository;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportJobServiceImpl implements ImportJobService {

    /** How many rows to show under the column matching as a sanity check. */
    private static final int SAMPLE_ROWS = 5;

    /** Matches the multipart limit; stated here so the message can be ours. */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /** The states a check may be started from — including after a failed one. */
    private static final Set<ImportStatus> CHECKABLE = EnumSet.of(
            ImportStatus.UPLOADED,
            ImportStatus.MAPPED,
            ImportStatus.READY,
            ImportStatus.VALIDATION_FAILED
    );

    private final BusinessHelper businessHelper;
    private final ImportJobRepository importJobRepository;
    private final ImportRowRepository importRowRepository;
    private final SourceFileParserRegistry parserRegistry;
    private final ImportProcessingService processingService;
    private final ImportJobStateService jobStateService;
    private final ImportRevertService revertService;
    private final UnitSheetReader unitSheetReader;
    private final UnitRepository unitRepository;
    private final ImportJobMapper mapper;
    private final MinioService minioService;

    @Qualifier("importTaskExecutor")
    private final TaskExecutor importTaskExecutor;

    @Override
    @Transactional(readOnly = true)
    public String buildTemplate(UUID businessId, ImportTargetType targetType) {
        // Nothing here is the shop's own data, but the check still runs: a
        // caller with no business to speak of has no business asking.
        businessHelper.findAccessibleBusiness(businessId);

        return ImportTemplate.csvFor(targetType);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] buildSample(UUID businessId, ImportSample sample) {
        businessHelper.findAccessibleBusiness(businessId);

        return ImportTemplate.xlsxFor(sample);
    }

    /**
     * The starting files worth offering for one kind of import.
     *
     * Served rather than written into the screen so the words describing a
     * sample and the columns inside it cannot drift apart.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ImportSampleResponse> findSamples(UUID businessId, ImportTargetType targetType) {
        businessHelper.findAccessibleBusiness(businessId);

        return ImportSample.forTarget(targetType).stream()
                .map(sample -> new ImportSampleResponse(
                        sample.name(),
                        sample.getLabel(),
                        sample.getDescription(),
                        sample.getFileName(),
                        sample.getColumns().stream().map(ImportField::getLabel).toList()
                ))
                .toList();
    }

    // --- upload --------------------------------------------------------------------

    @Override
    @Transactional
    public ImportJobResponse upload(UUID businessId, ImportTargetType targetType, MultipartFile file) {
        return uploadFor(businessHelper.findAccessibleBusiness(businessId), targetType, file);
    }

    /**
     * The upload itself, once the caller has been allowed in.
     *
     * Split from the check so assisted migration can hand its prepared workbook
     * to exactly this code without the tenant rule having to be relaxed for
     * every other feature that shares it.
     */
    private ImportJobResponse uploadFor(
            Business business, ImportTargetType targetType, MultipartFile file) {
        UUID businessId = business.getId();

        validateUpload(file);

        SourceFileParser parser = parserRegistry.parserFor(file.getOriginalFilename());
        SourceFileParser.SourceHeader header = readHeader(parser, file);

        ImportJob job = new ImportJob();
        job.setBusiness(business);
        job.setStartedByUserId(AuthHelper.currentUserId());
        job.setTargetType(targetType);
        job.setSourceType(parser.sourceType());
        job.setStatus(ImportStatus.UPLOADED);
        job.setSourceFileName(file.getOriginalFilename());
        job.setSourceFileSize(file.getSize());
        job.setSourceColumns(header.columns());
        job.setSampleRows(header.sample().stream().map(SourceRow::values).toList());

        /*
         * Read now, while the file is in hand. A workbook that declares its own
         * units lets one file describe everything it needs — and reading it
         * here rather than at commit means checking and the commit judge the
         * same declarations.
         */
        job.setDeclaredUnits(readDeclaredUnits(parser, file));

        /*
         * The file is stored only once its headings have been read. An upload
         * we cannot make sense of is refused before anything is kept, rather
         * than leaving an object nobody will ever ask for again.
         */
        job.setStorageObjectKey(minioService.uploadImportFile(file, businessId));

        return mapper.toResponse(importJobRepository.save(job));
    }

    private List<DeclaredUnit> readDeclaredUnits(SourceFileParser parser, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return unitSheetReader.read(parser, input);
        } catch (IOException e) {
            // A file whose headings read perfectly well but whose Units sheet
            // does not is a file with no declared units, not a failed upload.
            return List.of();
        }
    }

    /**
     * What the import will do about units, worked out from the same
     * declarations checking used.
     *
     * Counted against the catalogue rather than against the rows: a workbook
     * declaring Bag once is one unit to create however many items name it.
     */
    private ImportUnitSummary summariseUnits(ImportJob job) {
        List<DeclaredUnit> declared = job.getDeclaredUnits();

        if (declared == null || declared.isEmpty()) {
            return new ImportUnitSummary(0, 0, 0, List.of(), List.of());
        }

        UUID businessId = job.getBusiness().getId();
        List<String> toReuse = new ArrayList<>();
        List<String> toCreate = new ArrayList<>();
        int conflicts = 0;

        for (DeclaredUnit unit : declared) {
            List<Unit> existing = unitRepository.findSelectableUnitsNamed(businessId, unit.name());

            if (existing.isEmpty()) {
                toCreate.add(unit.label());
            } else if (existing.stream().anyMatch(u -> u.getCategory() == unit.category())) {
                toReuse.add(unit.label());
            } else {
                conflicts++;
            }
        }

        return new ImportUnitSummary(
                toReuse.size(), toCreate.size(), conflicts, List.copyOf(toReuse), List.copyOf(toCreate));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file to import.");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Files must be 10 MB or smaller. Please split this one into parts."
            );
        }

        String name = file.getOriginalFilename();

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file has no name.");
        }
    }

    private SourceFileParser.SourceHeader readHeader(SourceFileParser parser, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return parser.readHeader(input, SAMPLE_ROWS);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file could not be read.", e);
        }
    }

    // --- reading -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse findJob(UUID businessId, UUID importId) {
        return mapper.toResponse(findOwnedJob(businessId, importId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ImportJobResponse> findJobs(UUID businessId, ImportStatus status, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);

        Page<ImportJob> jobs = status == null
                ? importJobRepository.findByBusinessIdOrderByCreatedDateDesc(businessId, pageable)
                : importJobRepository.findByBusinessIdAndStatusOrderByCreatedDateDesc(businessId, status, pageable);

        return PageResponse.from(jobs.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ImportColumnsResponse findColumns(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        List<ImportFieldResponse> fields = ImportField.forTarget(job.getTargetType()).stream()
                .map(field -> new ImportFieldResponse(
                        field.name(),
                        field.getLabel(),
                        field.getHelp(),
                        field.getType(),
                        field.requirementFor(job.getTargetType())
                ))
                .toList();

        Map<String, String> suggestions = ImportField
                .suggestAll(job.getSourceColumns(), job.getTargetType())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().name(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        return new ImportColumnsResponse(
                job.getSourceColumns(),
                fields,
                suggestions,
                job.getColumnMappings(),
                job.getSampleRows(),
                ImportField.UNIT.appliesTo(job.getTargetType())
        );
    }

    // --- matching ------------------------------------------------------------------

    @Override
    @Transactional
    public ImportJobResponse saveMapping(UUID businessId, UUID importId, ImportMappingRequest request) {
        return applyMapping(findOwnedJob(businessId, importId), request);
    }

    private ImportJobResponse applyMapping(ImportJob job, ImportMappingRequest request) {
        requireNotFinished(job);

        Map<String, String> mappings = cleanMappings(job, request.mappings());
        requireAllRequiredFields(job.getTargetType(), mappings, request);

        job.setColumnMappings(mappings);
        job.setDuplicateStrategy(resolveStrategy(job.getTargetType(), request.duplicateStrategy()));
        job.setDefaultUnitId(request.defaultUnitId());
        job.setDefaultItemType(request.defaultItemType());
        job.setStatus(ImportStatus.MAPPED);

        return mapper.toResponse(importJobRepository.save(job));
    }

    /**
     * Keeps only real matches, and refuses two columns feeding one field.
     *
     * A field fed twice is not something to resolve by picking one: the shop
     * meant something by both, and only they know which.
     */
    private Map<String, String> cleanMappings(ImportJob job, Map<String, String> requested) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        Map<String, String> columnByField = new LinkedHashMap<>();

        requested.forEach((column, fieldName) -> {
            if (fieldName == null || fieldName.isBlank()) {
                return;
            }

            if (!job.getSourceColumns().contains(column)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "There is no column called \"" + column + "\" in this file."
                );
            }

            ImportField field = parseField(fieldName);

            if (!field.appliesTo(job.getTargetType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        field.getLabel() + " cannot be set by this kind of import."
                );
            }

            String alreadyMatched = columnByField.put(field.name(), column);

            if (alreadyMatched != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + alreadyMatched + "\" and \"" + column + "\" are both matched to "
                                + field.getLabel() + ". Please pick one."
                );
            }

            cleaned.put(column, field.name());
        });

        return cleaned;
    }

    private ImportField parseField(String fieldName) {
        try {
            return ImportField.valueOf(fieldName);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "\"" + fieldName + "\" is not a field that can be imported.", e);
        }
    }

    /**
     * Refuses a matching that could not possibly produce a usable row.
     *
     * Caught here rather than left to show up as ten thousand identical row
     * errors after a check that took a minute to run.
     */
    private void requireAllRequiredFields(
            ImportTargetType targetType,
            Map<String, String> mappings,
            ImportMappingRequest request
    ) {
        Set<String> matched = Set.copyOf(mappings.values());

        for (ImportField field : ImportField.forTarget(targetType)) {
            ImportFieldRequirement requirement = field.requirementFor(targetType);

            if (requirement == ImportFieldRequirement.REQUIRED && !matched.contains(field.name())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        field.getLabel() + " is required. Please match a column to it."
                );
            }

            if (requirement == ImportFieldRequirement.REQUIRED_OR_DEFAULTED
                    && !matched.contains(field.name())
                    && request.defaultUnitId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        field.getLabel() + " is required. Match a column to it, or choose one for the whole file."
                );
            }
        }

        Set<ImportField> identifiers = ImportField.identifiersFor(targetType);

        if (!identifiers.isEmpty()
                && identifiers.stream().noneMatch(field -> matched.contains(field.name()))) {
            String options = identifiers.stream().map(ImportField::getLabel).collect(Collectors.joining(", "));

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Match a column that says which item each row is for: " + options + "."
            );
        }
    }

    /**
     * Opening stock has no safe way to update, so it always skips.
     *
     * An opening balance is the first entry in an item's ledger and there can
     * only ever be one; re-importing a file would otherwise add the same stock
     * to the shelf twice. Rather than offer a choice that cannot be honoured,
     * the request is quietly held to the only one that can.
     */
    private ImportDuplicateStrategy resolveStrategy(
            ImportTargetType targetType,
            ImportDuplicateStrategy requested
    ) {
        return targetType == ImportTargetType.OPENING_STOCK ? ImportDuplicateStrategy.SKIP : requested;
    }

    // --- checking ------------------------------------------------------------------

    @Override
    @Transactional
    public ImportJobResponse startValidation(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        if (job.getColumnMappings().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Match your columns before checking the file.");
        }

        claim(job, businessId, ImportStatus.VALIDATING, CHECKABLE, "This import is already being checked.");

        runAfterCommit(
                () -> processingService.validate(importId),
                () -> jobStateService.failValidation(
                        importId, "Checking could not be started. Please try again.")
        );

        job.setStatus(ImportStatus.VALIDATING);

        return mapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportPreviewResponse findPreview(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        boolean updating = job.getDuplicateStrategy() == ImportDuplicateStrategy.UPDATE_EXISTING;
        int duplicates = orZero(job.getDuplicateRows());

        return new ImportPreviewResponse(
                job.getId(),
                job.getTargetType(),
                job.getStatus(),
                job.getDuplicateStrategy(),
                orZero(job.getTotalRows()),
                orZero(job.getValidRows()),
                duplicates,
                orZero(job.getInvalidRows()),
                /*
                 * What will be made, not how many rows go into making it: a
                 * file listing one row per option takes five rows to describe
                 * one shirt, and promising five items would be a lie the shop
                 * only discovers afterwards. Equal to the valid row count on an
                 * ordinary file, where a row is an item.
                 */
                willCreate(job),
                updating ? duplicates : 0,
                updating ? 0 : duplicates,
                orZero(job.getInvalidRows()),
                countItemGroupsToCreate(job),
                orZero(job.getOpeningStockRows()),
                summariseUnits(job),
                mapper.isCommittable(job)
        );
    }

    /**
     * How many things the import would create.
     *
     * Falls back to the valid row count for jobs checked before the count of
     * items was recorded, which is the same number on any file without options.
     */
    private int willCreate(ImportJob job) {
        int entities = orZero(job.getEntitiesToCreate());

        return entities > 0 ? entities : orZero(job.getValidRows());
    }

    /**
     * How many categories the import would bring into being.
     *
     * Counted from the staged rows, where the check already noted each one it
     * did not recognise. Distinct, because a hundred items in one new category
     * are one new category.
     */
    private int countItemGroupsToCreate(ImportJob job) {
        if (job.getTargetType() != ImportTargetType.ITEM) {
            return 0;
        }

        return (int) importRowRepository
                .findByImportJobIdAndStatusInOrderByRowNumberAsc(
                        job.getId(), List.of(ImportRowStatus.VALID, ImportRowStatus.DUPLICATE))
                .stream()
                .filter(row -> row.getIssues() != null)
                .filter(row -> row.getIssues().stream()
                        .anyMatch(issue -> "ITEM_GROUP_WILL_BE_CREATED".equals(issue.code())))
                .map(row -> String.valueOf(row.getNormalizedData().get("itemGroup")).toLowerCase())
                .distinct()
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ImportRowResponse> findRows(
            UUID businessId,
            UUID importId,
            ImportRowStatus status,
            Pageable pageable
    ) {
        ImportJob job = findOwnedJob(businessId, importId);

        Page<ImportRow> rows = status == null
                ? importRowRepository.findByImportJobIdOrderByRowNumberAsc(job.getId(), pageable)
                : importRowRepository.findByImportJobIdAndStatusOrderByRowNumberAsc(job.getId(), status, pageable);

        return PageResponse.from(rows.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ImportRowResponse> findFailedRows(UUID businessId, UUID importId, Pageable pageable) {
        ImportJob job = findOwnedJob(businessId, importId);

        Page<ImportRow> rows = importRowRepository.findByImportJobIdAndStatusInOrderByRowNumberAsc(
                job.getId(),
                List.of(ImportRowStatus.INVALID, ImportRowStatus.FAILED),
                pageable
        );

        return PageResponse.from(rows.map(mapper::toResponse));
    }

    // --- committing ----------------------------------------------------------------

    @Override
    @Transactional
    public ImportJobResponse startCommit(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        if (job.getStatus() == ImportStatus.COMMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This import has already been done.");
        }

        if (!mapper.isCommittable(job)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Check the file first. Only an import that has passed checking can be brought in."
            );
        }

        /*
         * The one transition that must never happen twice. Two clicks, or two
         * tabs, race on this update rather than in the commit itself — the
         * second finds the status already moved and is turned away before a
         * single row is written.
         */
        claim(job, businessId, ImportStatus.COMMITTING, EnumSet.of(ImportStatus.READY),
                "This import is already being brought in.");

        runAfterCommit(
                () -> processingService.commit(importId),
                () -> jobStateService.finishCommit(
                        importId,
                        new ImportTotals(),
                        "The import could not be started. Nothing was brought in. Please try again."
                )
        );

        job.setStatus(ImportStatus.COMMITTING);

        return mapper.toResponse(job);
    }

    // --- undoing -------------------------------------------------------------------

    /**
     * Takes a committed import back out, in the background.
     *
     * Only an import that finished can be undone, and only once: the claim is
     * the same race-proof update the commit uses, so two clicks or two tabs
     * cannot both start deleting the same items.
     */
    @Override
    @Transactional
    public ImportJobResponse startRevert(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        if (!mapper.isRevertable(job)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an import that has been brought in, and still has something it created,"
                            + " can be undone."
            );
        }

        claim(job, businessId, ImportStatus.REVERTING,
                EnumSet.of(ImportStatus.COMMITTED, ImportStatus.REVERTED),
                "This import is already being undone.");

        runAfterCommit(
                () -> revertService.revert(importId),
                () -> jobStateService.finishRevert(
                        importId,
                        new ImportRevertService.RevertTotals(),
                        "The undo could not be started. Nothing was removed. Please try again."
                )
        );

        job.setStatus(ImportStatus.REVERTING);

        return mapper.toResponse(job);
    }

    // --- reporting -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ImportReportResponse findReport(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        return new ImportReportResponse(
                job.getId(),
                job.getSourceFileName(),
                job.getTargetType(),
                job.getStatus(),
                job.getCreatedBy(),
                job.getCommitStartedAt() == null ? job.getCreatedDate() : job.getCommitStartedAt(),
                job.getCommitCompletedAt(),
                orZero(job.getTotalRows()),
                orZero(job.getCreatedRows()),
                orZero(job.getUpdatedRows()),
                orZero(job.getSkippedRows()),
                orZero(job.getFailedRows()),
                orZero(job.getInvalidRows()),
                orZero(job.getCreatedItemGroups()),
                orZero(job.getCreatedStockEntries()),
                job.getFailureMessage(),
                summariseErrors(businessId, importId)
        );
    }

    /**
     * The reasons rows were refused, most common first.
     *
     * A file with seven hundred bad rows usually has three problems in it, and
     * a shop fixing their spreadsheet needs to know which three — not to
     * page through seven hundred lines saying the same thing.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ImportReportResponse.ImportErrorSummary> summariseErrors(UUID businessId, UUID importId) {
        ImportJob job = findOwnedJob(businessId, importId);

        Map<String, List<RowIssue>> byCode = importRowRepository
                .findByImportJobIdAndStatusInOrderByRowNumberAsc(
                        job.getId(), List.of(ImportRowStatus.INVALID, ImportRowStatus.FAILED))
                .stream()
                .filter(row -> row.getIssues() != null)
                .flatMap(row -> row.getIssues().stream())
                .filter(RowIssue::isError)
                .collect(Collectors.groupingBy(RowIssue::code));

        return byCode.values().stream()
                .map(issues -> {
                    RowIssue first = issues.getFirst();

                    return new ImportReportResponse.ImportErrorSummary(
                            first.field(), first.code(), first.message(), issues.size());
                })
                .sorted(Comparator.comparingLong(ImportReportResponse.ImportErrorSummary::rows).reversed())
                .toList();
    }

    // --- shared --------------------------------------------------------------------

    private ImportJob findOwnedJob(UUID businessId, UUID importId) {
        businessHelper.findAccessibleBusiness(businessId);

        return jobOf(businessId, importId);
    }

    /** The job, having established elsewhere that the caller may see it. */
    private ImportJob jobOf(UUID businessId, UUID importId) {
        return importJobRepository.findByIdAndBusinessId(importId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That import has not been found"));
    }

    // --- the handover assisted migration uses --------------------------------------

    @Override
    @Transactional
    public ImportJobResponse uploadAsOperator(
            UUID businessId, ImportTargetType targetType, MultipartFile file) {
        return uploadFor(businessHelper.findBusinessForOperator(businessId), targetType, file);
    }

    @Override
    @Transactional
    public ImportJobResponse saveMappingAsOperator(
            UUID businessId, UUID importId, ImportMappingRequest request) {
        businessHelper.findBusinessForOperator(businessId);

        return applyMapping(jobOf(businessId, importId), request);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse findJobAsOperator(UUID businessId, UUID importId) {
        businessHelper.findBusinessForOperator(businessId);

        return mapper.toResponse(jobOf(businessId, importId));
    }

    private void requireNotFinished(ImportJob job) {
        if (job.getStatus() == ImportStatus.COMMITTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This import has already been done and cannot be changed.");
        }

        if (job.getStatus() == ImportStatus.VALIDATING || job.getStatus() == ImportStatus.COMMITTING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This import is still running. Please wait for it to finish.");
        }
    }

    /** Takes the job into a working state, or says somebody else already did. */
    private void claim(
            ImportJob job,
            UUID businessId,
            ImportStatus next,
            Set<ImportStatus> allowed,
            String refusal
    ) {
        int moved = importJobRepository.moveToStatus(job.getId(), businessId, next, allowed);

        if (moved == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, refusal);
        }
    }

    /**
     * Starts background work once the claim is actually saved.
     *
     * Handing it to the pool from inside the transaction is a race the pool
     * usually wins: the worker opens its own connection, reads a job whose new
     * status has not been committed yet, and either does the work twice or
     * concludes there is nothing to do.
     *
     * By the time this runs the job has already been marked as working, and
     * that mark is committed. So a pool that will not take the work leaves a
     * job claiming to run with nothing running it — which is why the handover
     * releases the job itself rather than trusting the periodic sweep to
     * notice half an hour later.
     */
    private void runAfterCommit(Runnable work, Runnable onRejected) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            handOver(work, onRejected);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                handOver(work, onRejected);
            }
        });
    }

    private void handOver(Runnable work, Runnable onRejected) {
        try {
            importTaskExecutor.execute(work);
        } catch (RuntimeException e) {
            log.error("Could not hand import work to the pool", e);

            try {
                onRejected.run();
            } catch (RuntimeException releaseFailure) {
                log.error("Could not release the import after a failed handover", releaseFailure);
            }

            throw e;
        }
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
