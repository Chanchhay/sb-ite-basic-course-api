package kh.edu.istad.ite.features.minio;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface MinioService {
    String uploadAsset(MultipartFile file);
    String getPublicUrl(String objectName);
    void deleteAsset(String objectName);

    /**
     * Stores a migration upload and returns the key it was stored under.
     *
     * The key is filed under the business so one shop's uploads can never be
     * confused with another's, and it never leaves the server: the browser
     * refers to an upload by its import job, and the job is what the ownership
     * check is made against.
     */
    String uploadImportFile(MultipartFile file, UUID businessId);

    /** Reads a migration upload back. The caller closes the stream. */
    InputStream readImportFile(String objectKey);

    void deleteImportFile(String objectKey);
}
