package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.dto.ImportColumnsResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportMappingRequest;
import kh.edu.istad.ite.features.dataimport.dto.ImportPreviewResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportReportResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportRowResponse;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The guided migration, from a file arriving to a report about what it did.
 *
 * Every method takes the business id and checks it. An import job id is the
 * only handle a client is ever given on an uploaded file, and one shop must
 * never be able to read — or commit — another's catalogue by quoting one.
 */
public interface ImportJobService {

    /**
     * A blank file to start from, already carrying the right column headings.
     *
     * The most useful thing this feature can hand someone who has never used
     * it: a file whose columns are matched automatically the moment they
     * upload it, so their first attempt succeeds rather than teaching them
     * what we expected.
     */
    String buildTemplate(UUID businessId, ImportTargetType targetType);

    /**
     * Takes the file, stores it, and reads its headings.
     *
     * Only the headings and a few sample rows, so the matching screen has
     * something to show at once. The rest of the file is not looked at until
     * the shop has said what its columns mean.
     */
    ImportJobResponse upload(UUID businessId, ImportTargetType targetType, MultipartFile file);

    ImportJobResponse findJob(UUID businessId, UUID importId);

    PageResponse<ImportJobResponse> findJobs(UUID businessId, ImportStatus status, Pageable pageable);

    /** The columns found in the file, the fields they can feed, and our guesses. */
    ImportColumnsResponse findColumns(UUID businessId, UUID importId);

    ImportJobResponse saveMapping(UUID businessId, UUID importId, ImportMappingRequest request);

    /** Starts checking in the background and returns immediately. */
    ImportJobResponse startValidation(UUID businessId, UUID importId);

    ImportPreviewResponse findPreview(UUID businessId, UUID importId);

    PageResponse<ImportRowResponse> findRows(
            UUID businessId, UUID importId, ImportRowStatus status, Pageable pageable);

    /** Just the rows that went wrong, for the errors view. */
    PageResponse<ImportRowResponse> findFailedRows(UUID businessId, UUID importId, Pageable pageable);

    /**
     * Starts the import in the background and returns immediately.
     *
     * Refuses unless the file has been checked and come out ready, and refuses
     * a second time however fast the second request arrives.
     */
    ImportJobResponse startCommit(UUID businessId, UUID importId);

    ImportReportResponse findReport(UUID businessId, UUID importId);

    /** The reasons rows were refused, worst first. */
    List<ImportReportResponse.ImportErrorSummary> summariseErrors(UUID businessId, UUID importId);
}
