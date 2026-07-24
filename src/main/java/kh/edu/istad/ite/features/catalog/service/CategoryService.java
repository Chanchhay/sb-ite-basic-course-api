package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateProductCategoryRequest;
import kh.edu.istad.ite.features.catalog.dto.ProductCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.ProductSubCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateProductCategoryRequest;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    ProductSubCategoryResponse createCategory(UUID businessId, CreateProductCategoryRequest request);

    List<ProductCategoryResponse> findAllCategories(UUID businessId);

    ProductSubCategoryResponse updateCategory(UUID businessId, UUID categoryId, UpdateProductCategoryRequest request);

    void deleteCategory(UUID businessId, UUID categoryId);
}
