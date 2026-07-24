package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.ProductCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.ProductSubCategoryResponse;
import kh.edu.istad.ite.features.catalog.entity.Category;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryMapper {

    public ProductSubCategoryResponse toSubCategoryResponse(Category category) {
        if (category == null) {
            return null;
        }

        return new ProductSubCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getNote(),
                category.getParent() == null ? null : category.getParent().getId()
        );
    }

    public ProductCategoryResponse toCategoryTreeResponse(
            Category category,
            List<Category> subCategories
    ) {
        return new ProductCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getNote(),
                subCategories.stream()
                        .map(this::toSubCategoryResponse)
                        .toList()
        );
    }
}
