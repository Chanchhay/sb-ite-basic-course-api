package kh.edu.istad.ite.features.minio.service.impl;


import kh.edu.istad.ite.features.minio.MinioService;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {
    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.bucket.assets}")
    private String assetsBucket;

    @Override
    public String uploadAsset(MultipartFile file) {
        return upload(file, assetsBucket);
    }

    private String upload(MultipartFile file, String bucket) {
        try {
            ensureBucketExists(bucket);

            String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), -1L)
                            .contentType(file.getContentType())
                            .build()
            );
            return fileName;
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    public String getPublicUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        if (objectName.startsWith("http://") || objectName.startsWith("https://")) {
            return objectName;
        }
        return endpoint + "/" + assetsBucket + "/" + objectName;
    }

    @Override
    public void deleteAsset(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(assetsBucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Delete failed: " + e.getMessage(), e);
        }
    }
}
