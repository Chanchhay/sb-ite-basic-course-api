package kh.edu.istad.ite.features.minio;

import org.springframework.web.multipart.MultipartFile;

public interface MinioService {
    String uploadAsset(MultipartFile file);
    String getPublicUrl(String objectName);
    void deleteAsset(String objectName);
}
