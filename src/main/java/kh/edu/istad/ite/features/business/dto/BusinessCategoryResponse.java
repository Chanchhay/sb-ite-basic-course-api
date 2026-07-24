package kh.edu.istad.ite.features.business.dto;

import java.util.UUID;
import java.util.List;

public record BusinessCategoryResponse(
        UUID id,
        String name,
        String slug,
        List<BusinessSubCategoryResponse> subCategories
) {
}
