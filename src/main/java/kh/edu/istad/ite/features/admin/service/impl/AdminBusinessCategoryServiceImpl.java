package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.request.BusinessCategoryUpsertRequest;
import kh.edu.istad.ite.features.admin.service.AdminBusinessCategoryService;
import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.dto.BusinessSubCategoryResponse;
import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCategoryRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminBusinessCategoryServiceImpl implements AdminBusinessCategoryService {

    private final BusinessCategoryRepository businessCategoryRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMapper businessMapper;

    @Override
    @Transactional(readOnly = true)
    public List<BusinessCategoryResponse> getCategoryTree() {
        Map<UUID, List<BusinessCategory>> subCategoriesByParentId =
                businessCategoryRepository.findByParentCategoryIsNotNullOrderByNameAsc()
                        .stream()
                        .collect(Collectors.groupingBy(category -> category.getParentCategory().getId()));

        return businessCategoryRepository.findByParentCategoryIsNullOrderByNameAsc()
                .stream()
                .map(category -> businessMapper.toCategoryTreeResponse(
                        category,
                        subCategoriesByParentId.getOrDefault(category.getId(), List.of())
                ))
                .toList();
    }

    @Override
    @Transactional
    public BusinessSubCategoryResponse createCategory(BusinessCategoryUpsertRequest request) {
        BusinessCategory category = new BusinessCategory();
        category.setName(request.name().trim());
        category.setIcon(StringUtils.hasText(request.icon()) ? request.icon().trim() : null);
        category.setParentCategory(resolveParent(request.parentId()));
        category.setSlug(generateUniqueSlug(request.name(), null));

        return toSubCategoryResponse(businessCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public BusinessSubCategoryResponse updateCategory(UUID categoryId, BusinessCategoryUpsertRequest request) {
        BusinessCategory category = findCategory(categoryId);

        String trimmedName = request.name().trim();
        if (!trimmedName.equals(category.getName())) {
            category.setName(trimmedName);
            category.setSlug(generateUniqueSlug(trimmedName, category.getId()));
        }

        category.setIcon(StringUtils.hasText(request.icon()) ? request.icon().trim() : null);

        BusinessCategory newParent = resolveParent(request.parentId());
        if (newParent != null && newParent.getId().equals(category.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category cannot be its own parent");
        }
        category.setParentCategory(newParent);

        return toSubCategoryResponse(businessCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID categoryId) {
        BusinessCategory category = findCategory(categoryId);

        if (businessCategoryRepository.countByParentCategory_Id(categoryId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a category that still has sub-categories");
        }

        if (businessRepository.existsByBusinessCategory_Id(categoryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a category that is still assigned to businesses");
        }

        businessCategoryRepository.delete(category);
    }

    private BusinessCategory resolveParent(UUID parentId) {
        if (parentId == null) {
            return null;
        }

        return businessCategoryRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category has not been found"));
    }

    private BusinessCategory findCategory(UUID categoryId) {
        return businessCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business category has not been found"));
    }

    private BusinessSubCategoryResponse toSubCategoryResponse(BusinessCategory category) {
        return businessMapper.toSubCategoryResponse(category);
    }

    private String generateUniqueSlug(String name, UUID excludedCategoryId) {
        String baseSlug = toSlugBase(name);
        String candidate = baseSlug;
        int suffix = 1;

        while (slugExists(candidate, excludedCategoryId)) {
            String suffixText = "-" + suffix;
            int baseMaxLength = 200 - suffixText.length();
            candidate = baseSlug.substring(0, Math.min(baseSlug.length(), baseMaxLength)).replaceAll("-$", "") + suffixText;
            suffix++;
        }

        return candidate;
    }

    private boolean slugExists(String slug, UUID excludedCategoryId) {
        if (excludedCategoryId == null) {
            return businessCategoryRepository.existsBySlug(slug);
        }

        return businessCategoryRepository.existsBySlugAndIdNot(slug, excludedCategoryId);
    }

    private String toSlugBase(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (!StringUtils.hasText(normalized)) {
            return "category";
        }

        return normalized.length() > 200 ? normalized.substring(0, 200).replaceAll("-$", "") : normalized;
    }
}
