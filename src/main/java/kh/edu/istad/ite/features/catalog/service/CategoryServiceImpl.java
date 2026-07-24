package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.CreateProductCategoryRequest;
import kh.edu.istad.ite.features.catalog.dto.ProductCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.ProductSubCategoryResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateProductCategoryRequest;
import kh.edu.istad.ite.features.catalog.entity.Category;
import kh.edu.istad.ite.features.catalog.mapper.CategoryMapper;
import kh.edu.istad.ite.features.catalog.repository.CategoryRepository;
import kh.edu.istad.ite.features.catalog.repository.ProductRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int SLUG_MAX_LENGTH = 200;
    private static final String SLUG_FALLBACK = "category";

    private final BusinessHelper businessHelper;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public ProductSubCategoryResponse createCategory(UUID businessId, CreateProductCategoryRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        Category parent = null;

        if (request.parentId() != null) {
            parent = categoryRepository.findByIdAndBusinessId(request.parentId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Parent category has not been found"
                    ));

            if (parent.getParent() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product categories support only 2 levels"
                );
            }
        }

        Category category = new Category();
        category.setBusiness(business);
        category.setParent(parent);
        category.setName(TextHelper.trimRequired(request.name(), "Category name cannot be empty"));
        category.setSlug(generateUniqueSlug(request.name(), businessId));
        category.setNote(TextHelper.trimToNull(request.note()));

        try {
            return categoryMapper.toSubCategoryResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> findAllCategories(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);

        Map<UUID, List<Category>> subCategoriesByParentId =
                categoryRepository.findByBusinessIdAndParentIsNotNullOrderByNameAsc(businessId)
                        .stream()
                        .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return categoryRepository.findByBusinessIdAndParentIsNullOrderByNameAsc(businessId)
                .stream()
                .map(category -> categoryMapper.toCategoryTreeResponse(
                        category,
                        subCategoriesByParentId.getOrDefault(category.getId(), List.of())
                ))
                .toList();
    }

    @Override
    @Transactional
    public ProductSubCategoryResponse updateCategory(
            UUID businessId,
            UUID categoryId,
            UpdateProductCategoryRequest request
    ) {
        businessHelper.findOwnedBusiness(businessId);
        Category category = findCategory(categoryId, businessId);

        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Category name cannot be empty");
            if (!name.equals(category.getName())) {
                category.setName(name);
                category.setSlug(generateUniqueSlug(name, businessId, categoryId));
            }
        }

        if (request.note() != null) {
            category.setNote(TextHelper.trimToNull(request.note()));
        }

        if (request.parentId() != null) {
            if (categoryId.equals(request.parentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent");
            }
            if (categoryRepository.existsByBusinessIdAndParentId(businessId, categoryId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Category with sub categories cannot become a sub category"
                );
            }
            category.setParent(findMainCategory(request.parentId(), businessId));
        }

        try {
            return categoryMapper.toSubCategoryResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteCategory(UUID businessId, UUID categoryId) {
        businessHelper.findOwnedBusiness(businessId);
        Category category = findCategory(categoryId, businessId);

        if (categoryRepository.existsByBusinessIdAndParentId(businessId, categoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete category with sub categories"
            );
        }
        if (productRepository.existsByBusinessIdAndCategoryId(businessId, categoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete category that is used by products"
            );
        }

        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    private String generateUniqueSlug(String name, UUID businessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> categoryRepository.existsByBusinessIdAndSlugIgnoreCase(businessId, slug)
        );
    }

    private String generateUniqueSlug(String name, UUID businessId, UUID excludedCategoryId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> categoryRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(
                        businessId,
                        slug,
                        excludedCategoryId
                )
        );
    }

    private Category findCategory(UUID categoryId, UUID businessId) {
        return categoryRepository.findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category has not been found"));
    }

    private Category findMainCategory(UUID categoryId, UUID businessId) {
        Category category = findCategory(categoryId, businessId);
        if (category.getParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent category must be a main category");
        }

        return category;
    }
}
