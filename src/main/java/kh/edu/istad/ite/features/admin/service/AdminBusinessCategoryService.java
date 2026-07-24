package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.request.BusinessCategoryUpsertRequest;
import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.dto.BusinessSubCategoryResponse;

import java.util.List;
import java.util.UUID;

public interface AdminBusinessCategoryService {

    List<BusinessCategoryResponse> getCategoryTree();

    BusinessSubCategoryResponse createCategory(BusinessCategoryUpsertRequest request);

    BusinessSubCategoryResponse updateCategory(UUID categoryId, BusinessCategoryUpsertRequest request);

    void deleteCategory(UUID categoryId);
}
