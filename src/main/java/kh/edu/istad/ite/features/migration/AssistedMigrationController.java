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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** Profiles every column and suggests what each one is. */
    @PostMapping("/{migrationId}/analyze")
    public AssistedMigrationDtos.AnalysisResponse analyze(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId
    ) {
        AssistedMigrationService.Analysis analysis = migrationService.analyze(businessId, migrationId);

        return new AssistedMigrationDtos.AnalysisResponse(
                analysis.profile().rows(),
                analysis.profile().columns().stream().map(mapper::toResponse).toList(),
                analysis.suggestions().stream().map(mapper::toResponse).toList(),
                mapper.targetFields(
                        migrationService.findOwned(businessId, migrationId).getTargetImportType())
        );
    }

    @PutMapping("/{migrationId}/mapping")
    public AssistedMigrationDtos.MigrationResponse saveMapping(
            @PathVariable UUID businessId,
            @PathVariable UUID migrationId,
            @Valid @RequestBody AssistedMigrationDtos.MappingRequest request
    ) {
        return mapper.toResponse(
                migrationService.saveMapping(businessId, migrationId, request.mappings()));
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
