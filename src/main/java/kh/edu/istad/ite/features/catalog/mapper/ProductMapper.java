package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.ProductResponse;
import kh.edu.istad.ite.features.catalog.dto.ProductVariantResponse;
import kh.edu.istad.ite.features.catalog.entity.Product;
import kh.edu.istad.ite.features.catalog.entity.ProductVariant;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    private final CategoryMapper categoryMapper;
    private final UnitMapper unitMapper;

    public ProductMapper(CategoryMapper categoryMapper, UnitMapper unitMapper) {
        this.categoryMapper = categoryMapper;
        this.unitMapper = unitMapper;
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getBusiness().getId(),
                categoryMapper.toSubCategoryResponse(product.getCategory()),
                product.getUnit() == null ? null : unitMapper.toResponse(product.getUnit()),
                product.getSlug(),
                product.getName(),
                product.getSku(),
                product.getCode(),
                product.getDescription(),
                product.getImageUrl(),
                product.getBarcode(),
                product.getPrice(),
                product.getItemType(),
                product.getAttributes(),
                product.getVariants().stream()
                        .map(this::toVariantResponse)
                        .toList(),
                product.getLowStockDefault(),
                product.getStatus()
        );
    }

    private ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSlug(),
                variant.getVariantName(),
                variant.getPrice()
        );
    }
}
