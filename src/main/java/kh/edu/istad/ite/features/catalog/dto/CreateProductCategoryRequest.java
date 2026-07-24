package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProductCategoryRequest(
        @NotBlank(message = "name cannot be empty")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 255, message = "note must be at most 255 characters")
        String note,

        UUID parentId
) {
}
