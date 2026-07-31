package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderItemImagesRequest(
        @NotEmpty(message = "imageIds list cannot be empty")
        List<UUID> imageIds
) {
}
