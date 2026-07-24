package kh.edu.istad.ite.features.catalog.dto;

import java.util.UUID;

public record ProductSubCategoryResponse(
        UUID id,
        String name,
        String slug,
        String note,
        UUID parentId
) {
}
