package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.CreateProductCategoryRequest;
import kh.edu.istad.ite.features.catalog.dto.ProductCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.ProductSubCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateProductCategoryRequest;
import kh.edu.istad.ite.features.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/product-categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ProductSubCategoryResponse createCategory(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateProductCategoryRequest request
    ) {
        return categoryService.createCategory(businessId, request);
    }

    @GetMapping
    public List<ProductCategoryResponse> findAllCategories(@PathVariable UUID businessId) {
        return categoryService.findAllCategories(businessId);
    }

    @PutMapping("/{categoryId}")
    public ProductSubCategoryResponse updateCategory(
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateProductCategoryRequest request
    ) {
        return categoryService.updateCategory(businessId, categoryId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{categoryId}")
    public void deleteCategory(
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId
    ) {
        categoryService.deleteCategory(businessId, categoryId);
    }
}
