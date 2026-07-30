package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantResponse;
import kh.edu.istad.ite.features.catalog.entity.DescriptionBlock;
import kh.edu.istad.ite.features.catalog.entity.DescriptionColumn;
import kh.edu.istad.ite.features.catalog.entity.Item;
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
                item.getBarcode(),
                item.getPrice(),
                item.getCompareAtPrice(),
                item.getItemType(),
                item.getAttributes() == null ? null : item.getAttributes().stream()
                        .map(this::toAttributeResponse)
                        .toList(),
                item.getDescriptionBlocks() == null ? null : item.getDescriptionBlocks().stream()
                        .map(this::toDescriptionBlockResponse)
                        .toList(),
                item.getVariants().stream()
                        .map(this::toVariantResponse)
                        .toList(),
                item.getLowStockDefault(),
                item.getStatus()
        );
    }

        );
    }

    private ItemVariantResponse toVariantResponse(ItemVariant variant) {
        return new ItemVariantResponse(
                variant.getId(),
                variant.getSlug(),
                variant.getVariantName(),
                variant.getPrice(),
                variant.getAvailable()
        );
    }
}
