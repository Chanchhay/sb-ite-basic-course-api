package kh.edu.istad.ite.features.catalog.dto;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record UploadItemImagesRequest(
        List<MultipartFile> files
) {
}
