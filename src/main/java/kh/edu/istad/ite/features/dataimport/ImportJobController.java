package kh.edu.istad.ite.features.dataimport;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.dataimport.dto.ImportColumnsResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportMappingRequest;
import kh.edu.istad.ite.features.dataimport.dto.ImportPreviewResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportReportResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportRowResponse;
import kh.edu.istad.ite.features.dataimport.field.ImportSample;
import kh.edu.istad.ite.features.dataimport.dto.ImportSampleResponse;
import kh.edu.istad.ite.features.dataimport.field.ImportTemplate;
import kh.edu.istad.ite.features.dataimport.service.ImportJobService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Migrating a shop's data in from whatever it used before.
 *
 * The workflow is deliberately several calls rather than one: upload, match
 * the columns, check, look at what will happen, and only then import. Nothing
 * outside the import's own staging is written until the last of those, which
 * is what lets a shop change its mind at any point before it.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/imports")
@RequiredArgsConstructor
public class ImportJobController {

    private final ImportJobService importJobService;

    /**
     * A blank file with the right column headings, for a shop that has none.
     *
     * A literal path segment, so it is matched ahead of {@code /{importId}}
     * rather than being read as an import whose id happens to be "template".
     *
     * Sent as an attachment with a name of its own: a CSV rendered in the
     * browser instead of saved is no use to anyone, whatever they meant to do
     * with it.
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @PathVariable UUID businessId,
            @RequestParam(required = false) ImportTargetType targetType,
            @RequestParam(required = false) ImportSample sample
    ) {
        /*
         * Either says which file is wanted. A shop choosing from the list sends
         * the sample; a link kept from before sends only the kind of import,
         * and gets that kind's first sample.
         */
        ImportSample wanted = sample != null
                ? sample
                : ImportSample.defaultFor(requireTargetType(targetType));

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ImportTemplate.fileNameFor(wanted) + "\""
                )
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importJobService.buildSample(businessId, wanted));
    }

    /** The starting files on offer for one kind of import. */
    @GetMapping("/samples")
    public List<ImportSampleResponse> findSamples(
            @PathVariable UUID businessId,
            @RequestParam ImportTargetType targetType
    ) {
        return importJobService.findSamples(businessId, targetType);
    }

    private ImportTargetType requireTargetType(ImportTargetType targetType) {
        if (targetType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Say which sample file you want.");
        }

        return targetType;
    }

    /**
     * Takes the file and reads its column headings.
     *
     * One call rather than the two the plan sketched — create, then attach a
     * file — because an import job without its file is a thing that can exist
     * in the database and mean nothing, and the screen has no step at which it
     * would show one.
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportJobResponse upload(
            @PathVariable UUID businessId,
            @RequestParam ImportTargetType targetType,
            @RequestPart("file") MultipartFile file
    ) {
        return importJobService.upload(businessId, targetType, file);
    }

    @GetMapping
    public PageResponse<ImportJobResponse> findAllImports(
            @PathVariable UUID businessId,
            @RequestParam(required = false) ImportStatus status,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return importJobService.findJobs(businessId, status, pageable);
    }

    /** Polled by the screen while a check or an import is running. */
    @GetMapping("/{importId}")
    public ImportJobResponse findImportById(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.findJob(businessId, importId);
    }

    @GetMapping("/{importId}/columns")
    public ImportColumnsResponse findColumns(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.findColumns(businessId, importId);
    }

    @PutMapping("/{importId}/mapping")
    public ImportJobResponse saveMapping(
            @PathVariable UUID businessId,
            @PathVariable UUID importId,
            @Valid @RequestBody ImportMappingRequest request
    ) {
        return importJobService.saveMapping(businessId, importId, request);
    }

    /**
     * Starts checking and returns at once.
     *
     * Accepted rather than OK: the answer is not ready, and the screen watches
     * the job's status for it.
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{importId}/validate")
    public ImportJobResponse startValidation(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.startValidation(businessId, importId);
    }

    @GetMapping("/{importId}/preview")
    public ImportPreviewResponse findPreview(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.findPreview(businessId, importId);
    }

    @GetMapping("/{importId}/rows")
    public PageResponse<ImportRowResponse> findRows(
            @PathVariable UUID businessId,
            @PathVariable UUID importId,
            @RequestParam(required = false) ImportRowStatus status,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return importJobService.findRows(businessId, importId, status, pageable);
    }

    /** The rows that went wrong, whether refused at checking or at import. */
    @GetMapping("/{importId}/errors")
    public PageResponse<ImportRowResponse> findErrors(
            @PathVariable UUID businessId,
            @PathVariable UUID importId,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return importJobService.findFailedRows(businessId, importId, pageable);
    }

    /**
     * Brings the checked rows into the catalogue.
     *
     * The only call in this controller that changes anything outside the
     * import's own staging, and the only one that cannot be undone. It refuses
     * an import that has not passed checking, and refuses a second time
     * however quickly the second request arrives.
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{importId}/commit")
    public ImportJobResponse startCommit(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.startCommit(businessId, importId);
    }

    /**
     * Undoes a committed import.
     *
     * A deletion, so it sits behind item:delete rather than the permission that
     * brought the import in — bringing a price list in and taking a shelf of
     * items back out are not the same trust.
     */
    @PostMapping("/{importId}/revert")
    public ImportJobResponse startRevert(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.startRevert(businessId, importId);
    }

    @GetMapping("/{importId}/report")
    public ImportReportResponse findReport(
            @PathVariable UUID businessId,
            @PathVariable UUID importId
    ) {
        return importJobService.findReport(businessId, importId);
    }
}
