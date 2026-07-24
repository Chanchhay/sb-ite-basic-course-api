package kh.edu.istad.ite.features.business.dto;

import java.util.UUID;

public record BusinessSubCategoryResponse(
        UUID id,
        String name,
        String slug
) {
}
