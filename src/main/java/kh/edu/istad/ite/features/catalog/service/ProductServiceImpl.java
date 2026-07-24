package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.CreateProductRequest;
import kh.edu.istad.ite.features.catalog.dto.ProductResponse;
import kh.edu.istad.ite.features.catalog.dto.ProductVariantRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateProductRequest;
import kh.edu.istad.ite.features.catalog.entity.Category;
import kh.edu.istad.ite.features.catalog.entity.Product;
import kh.edu.istad.ite.features.catalog.entity.ProductVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.mapper.ProductMapper;
import kh.edu.istad.ite.features.catalog.repository.CategoryRepository;
import kh.edu.istad.ite.features.catalog.repository.ProductRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.shared.enums.ProductStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final int SLUG_MAX_LENGTH = 250;
    private static final int VARIANT_SLUG_MAX_LENGTH = 255;
    private static final String SLUG_FALLBACK = "product";
    private static final String VARIANT_SLUG_FALLBACK = "variant";
    private static final int DEFAULT_LOW_STOCK = 20;

    private final BusinessHelper businessHelper;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse createProduct(UUID businessId, CreateProductRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        Product product = new Product();
        product.setBusiness(business);
        product.setCategory(findCategory(request.categoryId(), businessId));
        product.setUnit(findUnit(request.unitId()));
        String name = TextHelper.trimRequired(request.name(), "Product name cannot be empty");
        ensureProductNameIsUnique(businessId, name);
        product.setName(name);
        product.setSlug(generateUniqueSlug(name, businessId));
        product.setSku(TextHelper.trimToNull(request.sku()));
        product.setCode(TextHelper.trimToNull(request.code()));
        product.setDescription(TextHelper.trimToNull(request.description()));
        product.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
        product.setBarcode(TextHelper.trimToNull(request.barcode()));
        product.setPrice(normalizePrice(request.price()));
        product.setItemType(request.itemType());
        product.setAttributes(request.attributes());
        replaceVariants(product, business, request.variants());
        product.setLowStockDefault(request.lowStockDefault() == null ? DEFAULT_LOW_STOCK : request.lowStockDefault());
        product.setStatus(request.status() == null ? ProductStatus.ACTIVE : request.status());

        try {
            return productMapper.toResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> findAllProducts(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);
        return productRepository.findAllByBusinessIdOrderByNameAsc(businessId)
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findProductById(UUID businessId, UUID productId) {
        businessHelper.findOwnedBusiness(businessId);
        return productMapper.toResponse(findProduct(productId, businessId));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID businessId, UUID productId, UpdateProductRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Product product = findProduct(productId, businessId);

        if (request.categoryId() != null) {
            product.setCategory(findCategory(request.categoryId(), businessId));
        }
        if (request.unitId() != null) {
            product.setUnit(findUnit(request.unitId()));
        }
        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Product name cannot be empty");
            if (!name.equals(product.getName())) {
                ensureProductNameIsUnique(businessId, productId, name);
                product.setName(name);
                product.setSlug(generateUniqueSlug(name, businessId, productId));
            }
        }
        if (request.sku() != null) {
            product.setSku(TextHelper.trimToNull(request.sku()));
        }
        if (request.code() != null) {
            product.setCode(TextHelper.trimToNull(request.code()));
        }
        if (request.description() != null) {
            product.setDescription(TextHelper.trimToNull(request.description()));
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
        }
        if (request.barcode() != null) {
            product.setBarcode(TextHelper.trimToNull(request.barcode()));
        }
        if (request.price() != null) {
            product.setPrice(normalizePrice(request.price()));
        }
        if (request.itemType() != null) {
            product.setItemType(request.itemType());
        }
        if (request.attributes() != null) {
            product.setAttributes(request.attributes());
        }
        if (request.variants() != null) {
            replaceVariants(product, product.getBusiness(), request.variants());
        }
        if (request.lowStockDefault() != null) {
            product.setLowStockDefault(request.lowStockDefault());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }

        try {
            return productMapper.toResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteProduct(UUID businessId, UUID productId) {
        businessHelper.findOwnedBusiness(businessId);
        Product product = findProduct(productId, businessId);

        productRepository.delete(product);
        productRepository.flush();
    }

    private Product findProduct(UUID productId, UUID businessId) {
        return productRepository.findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product has not been found"));
    }

    private Category findCategory(UUID categoryId, UUID businessId) {
        if (categoryId == null) {
            return null;
        }

        Category category = categoryRepository.findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category has not been found"));

        if (categoryRepository.existsByBusinessIdAndParentId(businessId, categoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category with sub categories cannot be used for products"
            );
        }

        return category;
    }

    private Unit findUnit(UUID unitId) {
        if (unitId == null) {
            return null;
        }

        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit has not been found"));
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private void replaceVariants(
            Product product,
            Business business,
            List<ProductVariantRequest> variantRequests
    ) {
        product.getVariants().clear();
        if (variantRequests == null) {
            return;
        }

        Set<String> usedSlugs = new HashSet<>();
        for (ProductVariantRequest request : variantRequests) {
            String variantName = TextHelper.trimRequired(request.name(), "Variant name cannot be empty");

            ProductVariant variant = new ProductVariant();
            variant.setBusiness(business);
            variant.setProduct(product);
            variant.setVariantName(variantName);
            variant.setSlug(generateUniqueVariantSlug(variantName, usedSlugs));
            variant.setPrice(normalizePrice(request.price()));
            product.getVariants().add(variant);
        }
    }

    private String generateUniqueVariantSlug(String name, Set<String> usedSlugs) {
        String baseSlug = SlugHelper.toSlugBase(name, VARIANT_SLUG_FALLBACK, VARIANT_SLUG_MAX_LENGTH);
        String candidate = baseSlug;
        int suffix = 1;

        while (usedSlugs.contains(candidate)) {
            String suffixText = "-" + suffix;
            int baseMaxLength = VARIANT_SLUG_MAX_LENGTH - suffixText.length();
            candidate = SlugHelper.toSlugBase(baseSlug, VARIANT_SLUG_FALLBACK, baseMaxLength) + suffixText;
            suffix++;
        }

        usedSlugs.add(candidate);
        return candidate;
    }

    private String generateUniqueSlug(String name, UUID businessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> productRepository.existsByBusinessIdAndSlugIgnoreCase(businessId, slug)
        );
    }

    private void ensureProductNameIsUnique(UUID businessId, String name) {
        if (productRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product already exists");
        }
    }

    private void ensureProductNameIsUnique(UUID businessId, UUID excludedProductId, String name) {
        if (productRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, excludedProductId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product already exists");
        }
    }

    private String generateUniqueSlug(String name, UUID businessId, UUID excludedProductId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> productRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(
                        businessId,
                        slug,
                        excludedProductId
                )
        );
    }
}
