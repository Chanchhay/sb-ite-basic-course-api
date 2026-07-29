package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.ItemImageResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantResponse;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemImage;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.minio.MinioService;
import org.springframework.stereotype.Component;

@Component
public class ItemMapper {

    private final ItemGroupMapper itemGroupMapper;
    private final UnitMapper unitMapper;
    private final MinioService minioService;

    public ItemMapper(ItemGroupMapper itemGroupMapper, UnitMapper unitMapper, MinioService minioService) {
        this.itemGroupMapper = itemGroupMapper;
        this.unitMapper = unitMapper;
        this.minioService = minioService;
    }

    public ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getBusiness().getId(),
                itemGroupMapper.toSubItemGroupResponse(item.getItemGroup()),
                item.getUnit() == null ? null : unitMapper.toResponse(item.getUnit()),
                item.getSlug(),
                item.getName(),
                item.getSku(),
                item.getCode(),
                item.getDescription(),
                item.getImages().stream()
                        .map(this::toImageResponse)
                        .toList(),
                item.getBarcode(),
                item.getPrice(),
                item.getItemType(),
                item.getAttributes(),
                item.getVariants().stream()
                        .map(this::toVariantResponse)
                        .toList(),
                item.getLowStockDefault(),
                item.getStatus()
        );
    }

    private ItemImageResponse toImageResponse(ItemImage image) {
        return new ItemImageResponse(
                image.getId(),
                minioService.getPublicUrl(image.getImageKey()),
                image.getPosition()
        );
    }

    private ItemVariantResponse toVariantResponse(ItemVariant variant) {
        return new ItemVariantResponse(
                variant.getId(),
                variant.getSlug(),
                variant.getVariantName(),
                variant.getPrice()
        );
    }
}
