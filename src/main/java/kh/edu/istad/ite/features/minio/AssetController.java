package kh.edu.istad.ite.features.minio;

import kh.edu.istad.ite.features.minio.dto.AssetResponse;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/assets")
@RequiredArgsConstructor
public class AssetController {

    private final MinioService minioService;
    private final BusinessHelper businessHelper;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetResponse uploadAsset(
            @PathVariable UUID businessId,
            @RequestParam("file") MultipartFile file
    ) {
        businessHelper.findOwnedBusiness(businessId);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file cannot be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }

        String key = minioService.uploadAsset(file);
        String url = minioService.getPublicUrl(key);

        return new AssetResponse(key, url);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{key}")
    public void deleteAsset(
            @PathVariable UUID businessId,
            @PathVariable String key
    ) {
        businessHelper.findOwnedBusiness(businessId);
        if (key != null && !key.isBlank()) {
            minioService.deleteAsset(key);
        }
    }
}
