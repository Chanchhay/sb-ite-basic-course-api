package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.admin.dto.request.BusinessCategoryUpsertRequest;
import kh.edu.istad.ite.features.admin.service.AdminBusinessCategoryService;
import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.dto.BusinessSubCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/business-categories")
@RequiredArgsConstructor
public class AdminBusinessCategoryController {

    private final AdminBusinessCategoryService adminBusinessCategoryService;

    @GetMapping
    public List<BusinessCategoryResponse> getCategoryTree() {
        return adminBusinessCategoryService.getCategoryTree();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public BusinessSubCategoryResponse createCategory(@Valid @RequestBody BusinessCategoryUpsertRequest request) {
        return adminBusinessCategoryService.createCategory(request);
    }

    @PutMapping("/{categoryId}")
    public BusinessSubCategoryResponse updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody BusinessCategoryUpsertRequest request
    ) {
        return adminBusinessCategoryService.updateCategory(categoryId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{categoryId}")
    public void deleteCategory(@PathVariable UUID categoryId) {
        adminBusinessCategoryService.deleteCategory(categoryId);
    }
}
