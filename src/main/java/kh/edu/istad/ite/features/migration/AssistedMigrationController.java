package kh.edu.istad.ite.features.migration;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.migration.dto.AssistedMigrationDtos;
import kh.edu.istad.ite.features.migration.service.AssistedMigrationMapper;
import kh.edu.istad.ite.features.migration.service.AssistedMigrationService;
import lombok.RequiredArgsConstructor;
import kh.edu.istad.ite.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import kh.edu.istad.ite.shared.enums.MigrationSourcePurpose;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Assisted migration, for FluxiBiz staff working on a customer's behalf.
 *
 * Under {@code /admin} because that is who uses it: an operator handed a
 * shop's old POS export, not the shopkeeper. Business users keep the importer
 * they already have, and this feature ends by handing them an ordinary import
 * job on the same catalogue.
 */
@RestController
@RequestMapping("/api/v1/admin/businesses/{businessId}/assisted-migrations")
@RequiredArgsConstructor
public class AssistedMigrationController {

    private final AssistedMigrationService migrationService;
    private final AssistedMigrationMapper mapper;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public AssistedMigrationDtos.MigrationResponse create(
            @PathVariable UUID businessId,
            @Valid @RequestBody AssistedMigrationDtos.CreateRequest request
    ) {
        return mapper.toResponse(
                migrationService.create(businessId, request.targetType(), request.snapshotAt()));
    }

    /** Stores the customer's file, untouched, and reads its headings. */
    @PostMapping(path = "/{migrationId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssistedMigrationDtos.MigrationResponse attachFile(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @RequestPart("file") MultipartFile file
    ) {
        return mapper.toResponse(migrationService.attachFile(businessId, migrationId, file));
    }

    /**
     * Adds one of the customer's files.
     *
     * A migration may hold several. The first is the one whose rows become
     * items; the rest fill in what it left out, once they have been connected
     * to it.
     */
    @PostMapping(path = "/{migrationId}/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AssistedMigrationDtos.SourceResponse addSource(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "purpose", required = false) MigrationSourcePurpose purpose
    ) {
        return mapper.toResponse(
                migrationService.addSource(businessId, migrationId, file, purpose));
    }

    @GetMapping("/{migrationId}/sources")
    public List<AssistedMigrationDtos.SourceResponse> findSources(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.findSources(businessId, migrationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Removes a file, and any join that depended on it. */
    @DeleteMapping("/{migrationId}/sources/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSource(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @PathVariable UUID sourceId
    ) {
        migrationService.removeSource(businessId, migrationId, sourceId);
    }

    /** What a file is for. Guessed on upload, corrected here. */
    @PutMapping("/{migrationId}/sources/{sourceId}/purpose")
    public AssistedMigrationDtos.SourceResponse setPurpose(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @PathVariable UUID sourceId,
            @Valid @RequestBody AssistedMigrationDtos.SourcePurposeRequest request
    ) {
        return mapper.toResponse(migrationService.setSourcePurpose(
                businessId, migrationId, sourceId, request.purpose()));
    }

    @PutMapping("/{migrationId}/sources/{sourceId}/mapping")
    public AssistedMigrationDtos.SourceResponse saveSourceMapping(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @PathVariable UUID sourceId,
            @Valid @RequestBody AssistedMigrationDtos.SourceMappingRequest request
    ) {
        return mapper.toResponse(migrationService.saveSourceMapping(
                businessId, migrationId, sourceId, request.mappings()));
    }

    /** Profiles every column of every file and suggests what each one is. */
    @PostMapping("/{migrationId}/analyze")
    public AssistedMigrationDtos.AnalysisSummaryResponse analyze(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        List<AssistedMigrationService.Analysis> analyses =
                migrationService.analyzeAll(businessId, migrationId);

        return new AssistedMigrationDtos.AnalysisSummaryResponse(
                analyses.stream().map(mapper::toResponse).toList(),
                mapper.targetFields(
                        migrationService.findOwned(businessId, migrationId).getTargetImportType())
        );
    }

    /**
     * Which columns of the other files look like they identify the same records.
     *
     * Suggestions only. Each carries what the join would actually do, because
     * an operator asked to approve a relationship and then fetch the numbers
     * separately will approve it without fetching them.
     */
    @GetMapping("/{migrationId}/join-suggestions")
    public List<AssistedMigrationDtos.JoinSuggestionResponse> joinSuggestions(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.suggestJoins(businessId, migrationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Saves how the files relate.
     *
     * All at once: the joins only make sense together. A join that would
     * multiply rows is refused rather than warned about, because there is no
     * honest way to carry it out.
     */
    @PutMapping("/{migrationId}/source-relationships")
    public List<AssistedMigrationDtos.JoinQualityResponse> saveRelationships(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @Valid @RequestBody AssistedMigrationDtos.RelationshipsRequest request
    ) {
        migrationService.saveRelationships(businessId, migrationId, request.relationships());

        return migrationService.joinQuality(businessId, migrationId);
    }

    /** What the saved joins actually do, counted against the files. */
    @GetMapping("/{migrationId}/join-quality")
    public List<AssistedMigrationDtos.JoinQualityResponse> joinQuality(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.joinQuality(businessId, migrationId);
    }

    /**
     * Every field that matters, and how much of it the files supplied.
     *
     * Read after joining, because until then "missing" is not a fact — a
     * product with no unit in the product list may well have one in the price
     * list.
     */
    @GetMapping("/{migrationId}/missing-fields")
    public AssistedMigrationDtos.MissingFieldsResponse missingFields(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return mapper.toResponse(migrationService.missingFields(businessId, migrationId));
    }

    /**
     * Why one record came out the way it did.
     *
     * Every field, with the file, column and line it came from, or the rule
     * that settled it. The question this answers — "why is this item counted
     * in cans?" — has no other honest answer once several files have been
     * joined.
     */
    @GetMapping("/{migrationId}/rows/{rowNumber}/explain")
    public AssistedMigrationDtos.RowExplanation explainRow(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @PathVariable int rowNumber
    ) {
        return migrationService.explainRow(businessId, migrationId, rowNumber);
    }

    @PutMapping("/{migrationId}/mapping")
    public AssistedMigrationDtos.MigrationResponse saveMapping(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @Valid @RequestBody AssistedMigrationDtos.MappingRequest request
    ) {
        return mapper.toResponse(
                migrationService.saveMapping(
                        businessId, migrationId, request.mappings(), request.optionAxisNames()));
    }

    /** Reads the file into FluxiBiz's terms and files what needs deciding. */
    @PostMapping("/{migrationId}/transform")
    public AssistedMigrationDtos.MigrationResponse transform(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return mapper.toResponse(migrationService.transform(businessId, migrationId));
    }

    @GetMapping("/{migrationId}/issues")
    public List<AssistedMigrationDtos.IssueResponse> findIssues(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.findIssues(businessId, migrationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** One decision, applied to every row that raised the same question. */
    @PutMapping("/{migrationId}/issues/{issueId}/resolution")
    public AssistedMigrationDtos.IssueResponse resolve(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @PathVariable UUID issueId,
            @Valid @RequestBody AssistedMigrationDtos.ResolutionRequest request
    ) {
        return mapper.toResponse(
                migrationService.resolve(businessId, migrationId, issueId, request.resolution()));
    }

    /**
     * Hands the prepared data to the importer every shop uses.
     *
     * Returns the import job. Everything after this — checking, review, commit,
     * the report — is the existing flow, unchanged.
     */
    @PostMapping("/{migrationId}/prepare-import")
    public ImportJobResponse prepareImport(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.prepareImport(businessId, migrationId);
    }

    /** What handing this over would do, before anybody agrees to it. */
    @GetMapping("/{migrationId}/summary")
    public AssistedMigrationDtos.PreparedSummary summary(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.summarise(businessId, migrationId);
    }

    /** The workbook that would be handed over, for anyone who wants to look. */
    @GetMapping("/{migrationId}/prepared-file")
    public ResponseEntity<byte[]> preparedFile(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"fluxibiz-prepared.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(migrationService.preparedWorkbook(businessId, migrationId));
    }

    /**
     * Records what each source record became, once the import has been brought in.
     *
     * Nothing needs this today. It is what makes a later delta migration able to
     * answer "P001 changed" without guessing.
     */
    @PostMapping("/{migrationId}/link-entities")
    public int linkEntities(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return migrationService.linkImportedEntities(businessId, migrationId);
    }

    /** Every migration for this shop, newest first. */
    @GetMapping
    public PageResponse<AssistedMigrationDtos.MigrationResponse> findAll(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return PageResponse.from(
                migrationService.findAll(businessId, pageable).map(mapper::toResponse));
    }

    @GetMapping("/{migrationId}")
    public AssistedMigrationDtos.MigrationResponse findOne(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        return mapper.toResponse(migrationService.findOwned(businessId, migrationId));
    }
}
