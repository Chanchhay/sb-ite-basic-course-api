package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.DescriptionBlockResponse;
import kh.edu.istad.ite.features.catalog.dto.DescriptionColumnResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemAttributeResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemAttributeValueResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemColorResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemUomConversionResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantResponse;
import kh.edu.istad.ite.features.catalog.entity.DescriptionBlock;
import kh.edu.istad.ite.features.catalog.entity.DescriptionColumn;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemAttribute;
import kh.edu.istad.ite.features.catalog.entity.ItemAttributeValue;
import kh.edu.istad.ite.features.catalog.entity.ItemUomConversion;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.ItemImage;
import kh.edu.istad.ite.features.catalog.dto.ItemImageResponse;
import kh.edu.istad.ite.features.minio.MinioService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemMapper {

    private final ItemGroupMapper itemGroupMapper;
    private final UnitMapper unitMapper;
    private final AddOnMapper addOnMapper;
    private final MinioService minioService;

    public ItemMapper(
            ItemGroupMapper itemGroupMapper,
            UnitMapper unitMapper,
            AddOnMapper addOnMapper,
            MinioService minioService
    ) {
        this.itemGroupMapper = itemGroupMapper;
        this.unitMapper = unitMapper;
        this.addOnMapper = addOnMapper;
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
                item.getImageUrl(),
                item.getImages() == null ? null : item.getImages().stream()
                        .map(this::toItemImageResponse)
                        .toList(),
                item.getBadge(),
                item.getBarcode(),
                item.getPrice(),
                // Only a discount can say what an item used to cost.
                null,
                item.getItemType(),
                item.getTrackInventory(),
                item.getAttributes() == null ? null : item.getAttributes().stream()
                        .map(this::toAttributeResponse)
                        .toList(),
                item.getColors() == null ? List.of() : item.getColors().stream()
                        .map(color -> new ItemColorResponse(
                                color.getValue(),
                                color.getColorHex(),
                                color.getImageUrl()))
                        .toList(),
                item.getDescriptionBlocks() == null ? null : item.getDescriptionBlocks().stream()
                        .map(this::toDescriptionBlockResponse)
                        .toList(),
                item.getVariants().stream()
                        .map(this::toVariantResponse)
                        .toList(),
                item.getAddOns() == null ? List.of() : item.getAddOns().stream()
                        .map(addOnMapper::toResponse)
                        .toList(),
                item.getUomConversions() == null ? List.of() : item.getUomConversions().stream()
                        .map(this::toUomConversionResponse)
                        .toList(),
                item.getLowStockDefault(),
                item.getStatus(),
                // Left to whoever is asking on behalf of a channel; the
                // catalogue itself has no channel to answer for.
                null
        );
    }

    private ItemAttributeResponse toAttributeResponse(ItemAttribute attribute) {
        return new ItemAttributeResponse(
                attribute.getName(),
                attribute.getType(),
                attribute.getPlacement(),
                attribute.getIcon(),
                attribute.getValues() == null ? null : attribute.getValues().stream()
                        .map(this::toAttributeValueResponse)
                        .toList()
        );
    }

    private ItemAttributeValueResponse toAttributeValueResponse(ItemAttributeValue value) {
        return new ItemAttributeValueResponse(
                value.getValue(),
                value.getLabel(),
                value.getColorHex(),
                value.getAvailable()
        );
    }

    private DescriptionBlockResponse toDescriptionBlockResponse(DescriptionBlock block) {
        return new DescriptionBlockResponse(
                block.getType(),
                block.getText(),
                block.getItems(),
                block.getUrl(),
                block.getCaption(),
                block.getColumns() == null ? null : block.getColumns().stream()
                        .map(this::toDescriptionColumnResponse)
                        .toList()
        );
    }

    private DescriptionColumnResponse toDescriptionColumnResponse(DescriptionColumn column) {
        return new DescriptionColumnResponse(
                column.getBlocks() == null ? null : column.getBlocks().stream()
                        .map(this::toDescriptionBlockResponse)
                        .toList()
        );
    }

    private ItemVariantResponse toVariantResponse(ItemVariant variant) {
        return new ItemVariantResponse(
                variant.getId(),
                variant.getSlug(),
                variant.getVariantName(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getImageUrl(),
                variant.getPrice(),
                null,
                variant.getOptionName(),
                variant.getColorValue(),
                variant.getAvailable(),
                null
        );
    }

    private ItemUomConversionResponse toUomConversionResponse(ItemUomConversion conversion) {
        return new ItemUomConversionResponse(
                conversion.getId(),
                unitMapper.toResponse(conversion.getUnit()),
                conversion.getVariant() == null ? null : conversion.getVariant().getId(),
                conversion.getVariant() == null ? null : conversion.getVariant().getVariantName(),
                conversion.getFactor(),
                conversion.getPrice()
        );
    }

    private ItemImageResponse toItemImageResponse(ItemImage image) {
        return new ItemImageResponse(
                image.getId(),
                minioService.getPublicUrl(image.getImageKey()),
                image.getPosition()
        );
    }
}
