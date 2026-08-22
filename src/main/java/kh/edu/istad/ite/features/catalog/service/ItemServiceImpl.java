package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemUomConversionRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.ItemAddOn;
import kh.edu.istad.ite.features.catalog.entity.ItemUomConversion;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.features.catalog.repository.AddOnRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.catalog.entity.ItemImage;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;
import kh.edu.istad.ite.features.channel.entity.ItemChannel;
import org.springframework.web.multipart.MultipartFile;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kh.edu.istad.ite.features.catalog.dto.DescriptionBlockRequest;
import kh.edu.istad.ite.features.catalog.dto.DescriptionColumnRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemAttributeRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemColorRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemAttributeValueRequest;
import kh.edu.istad.ite.features.catalog.entity.DescriptionBlock;
import kh.edu.istad.ite.features.catalog.entity.DescriptionColumn;
import kh.edu.istad.ite.features.catalog.entity.ItemAttribute;
import kh.edu.istad.ite.features.catalog.entity.ItemColor;
import kh.edu.istad.ite.features.catalog.entity.ItemAttributeValue;
import kh.edu.istad.ite.shared.enums.AttributePlacement;
import kh.edu.istad.ite.shared.enums.AttributeType;
import kh.edu.istad.ite.shared.enums.DescriptionBlockType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.config.specification.FilterSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private static final int SLUG_MAX_LENGTH = 250;
    private static final int VARIANT_SLUG_MAX_LENGTH = 255;
    private static final String SLUG_FALLBACK = "item";
    private static final String VARIANT_SLUG_FALLBACK = "variant";
    private static final int DEFAULT_LOW_STOCK = 20;

    private final BusinessHelper businessHelper;
    private final ItemGroupRepository itemGroupRepository;
    private final ItemRepository itemRepository;
    private final FilterSpecification<Item> filterSpecification;
    private final UnitRepository unitRepository;
    private final AddOnRepository addOnRepository;
    private final ItemMapper itemMapper;
    private final MinioService minioService;
    private final ItemChannelRepository itemChannelRepository;

    @Override
    @Transactional
    public ItemResponse createItem(UUID businessId, CreateItemRequest request, List<MultipartFile> files) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        Item item = new Item();
        item.setBusiness(business);
        item.setItemGroup(findItemGroup(request.itemGroupId(), businessId));
        item.setUnit(findUnit(request.unitId(), businessId));
        String name = TextHelper.trimRequired(request.name(), "Item name cannot be empty");
        ensureItemNameIsUnique(businessId, name);
        validateAttributes(request.attributes());
        validateDescriptionBlocks(request.descriptionBlocks());
        item.setName(name);
        item.setSlug(generateUniqueSlug(name, businessId));
        item.setSku(TextHelper.trimToNull(request.sku()));
        item.setCode(TextHelper.trimToNull(request.code()));
        item.setDescription(TextHelper.trimToNull(request.description()));
        item.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
        item.setBarcode(TextHelper.trimToNull(request.barcode()));
        item.setPrice(normalizePrice(request.price()));
        item.setItemType(request.itemType());
        if (request.trackInventory() != null) {
            item.setTrackInventory(request.trackInventory());
        } else {
            item.setTrackInventory(request.itemType() == ItemType.PHYSICAL);
        }
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                String imageKey = minioService.uploadAsset(file);
                ItemImage image = new ItemImage();
                image.setItem(item);
                image.setImageKey(imageKey);
                image.setPosition(item.getImages().size());
                item.getImages().add(image);
            }
        }
        item.setBadge(TextHelper.trimToNull(request.badge()));
        item.setCompareAtPrice(normalizePrice(request.compareAtPrice()));
        item.setDescriptionBlocks(mapDescriptionBlocks(request.descriptionBlocks()));
        item.setAttributes(mapAttributes(request.attributes()));
        item.setColors(mapColors(request.colors()));
        replaceVariants(item, business, request.variants());
        requireDeclaredColors(item);
        replaceAddOns(item, businessId, request.addOnIds());
        replaceUomConversions(item, businessId, request.uomConversions());
        item.setLowStockDefault(request.lowStockDefault() == null ? DEFAULT_LOW_STOCK : request.lowStockDefault());
        item.setStatus(request.status() == null ? ItemStatus.ACTIVE : request.status());

        try {
            return itemMapper.toResponse(itemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException e) {
            throw itemConflict(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemResponse> findAllItems(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);
        return itemRepository.findAllByBusinessIdOrderByNameAsc(businessId)
                .stream()
                .map(itemMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ItemResponse findItemById(UUID businessId, UUID itemId) {
        businessHelper.findOwnedBusiness(businessId);
        return itemMapper.toResponse(findItem(itemId, businessId));
    }

    @Override
    @Transactional
    public ItemResponse updateItem(UUID businessId, UUID itemId, UpdateItemRequest request, List<MultipartFile> files) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);

        if (request.itemGroupId() != null) {
            item.setItemGroup(findItemGroup(request.itemGroupId(), businessId));
        }
        if (request.unitId() != null) {
            item.setUnit(findUnit(request.unitId(), businessId));
        }
        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Item name cannot be empty");
            if (!name.equals(item.getName())) {
                ensureItemNameIsUnique(businessId, itemId, name);
                item.setName(name);
                item.setSlug(generateUniqueSlug(name, businessId, itemId));
            }
        }
        
        if (request.attributes() != null) {
            validateAttributes(request.attributes());
        }
        if (request.descriptionBlocks() != null) {
            validateDescriptionBlocks(request.descriptionBlocks());
        }
        if (request.sku() != null) {
            item.setSku(TextHelper.trimToNull(request.sku()));
        }
        if (request.code() != null) {
            item.setCode(TextHelper.trimToNull(request.code()));
        }
        if (request.description() != null) {
            item.setDescription(TextHelper.trimToNull(request.description()));
        }
        if (request.imageUrl() != null) {
            item.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
        }
        if (request.barcode() != null) {
            item.setBarcode(TextHelper.trimToNull(request.barcode()));
        }
        if (request.price() != null) {
            item.setPrice(normalizePrice(request.price()));
        }
        if (request.itemType() != null) {
            item.setItemType(request.itemType());
        }
        if (request.trackInventory() != null) {
            item.setTrackInventory(request.trackInventory());
        }
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                String imageKey = minioService.uploadAsset(file);
                ItemImage image = new ItemImage();
                image.setItem(item);
                image.setImageKey(imageKey);
                image.setPosition(item.getImages().size());
                item.getImages().add(image);
            }
        }
        if (request.badge() != null) {
            item.setBadge(TextHelper.trimToNull(request.badge()));
        }
        if (request.compareAtPrice() != null) {
            item.setCompareAtPrice(normalizePrice(request.compareAtPrice()));
        }
        if (request.descriptionBlocks() != null) {
            item.setDescriptionBlocks(mapDescriptionBlocks(request.descriptionBlocks()));
        }
        if (request.attributes() != null) {
            item.setAttributes(mapAttributes(request.attributes()));
            item.setColors(mapColors(request.colors()));
        }
        if (request.variants() != null) {
            replaceVariants(item, item.getBusiness(), request.variants());
            requireDeclaredColors(item);
        }
        if (request.addOnIds() != null) {
            replaceAddOns(item, item.getBusiness().getId(), request.addOnIds());
        }
        if (request.uomConversions() != null) {
            replaceUomConversions(item, item.getBusiness().getId(), request.uomConversions());
        }
        if (request.lowStockDefault() != null) {
            item.setLowStockDefault(request.lowStockDefault());
        }
        if (request.status() != null) {
            item.setStatus(request.status());
        }

        try {
            return itemMapper.toResponse(itemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException e) {
            throw itemConflict(e);
        }
    }

    @Override
    @Transactional
    public ItemResponse updateItemAddOns(UUID businessId, UUID itemId, List<UUID> addOnIds) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);

        replaceAddOns(item, businessId, addOnIds);

        try {
            return itemMapper.toResponse(itemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException e) {
            throw itemConflict(e);
        }
    }

    @Override
    @Transactional
    public void deleteItem(UUID businessId, UUID itemId) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);

        List<ItemChannel> itemChannels = itemChannelRepository.findByItemId(itemId);
        itemChannelRepository.deleteAll(itemChannels);

        for (ItemImage image : item.getImages()) {
            minioService.deleteAsset(image.getImageKey());
        }

        try {
            itemRepository.delete(item);
            itemRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete item because it has been ordered or is in use", e);
        }
    }

    @Override
    @Transactional
    public ItemResponse deleteItemImage(UUID businessId, UUID itemId, UUID imageId) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);
        ItemImage imageToRemove = item.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        item.getImages().remove(imageToRemove);
        minioService.deleteAsset(imageToRemove.getImageKey());
        
        for (int i = 0; i < item.getImages().size(); i++) {
            item.getImages().get(i).setPosition(i);
        }
        
        return itemMapper.toResponse(itemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public ItemResponse uploadItemImages(UUID businessId, UUID itemId, kh.edu.istad.ite.features.catalog.dto.UploadItemImagesRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);
        if (request.files() != null && !request.files().isEmpty()) {
            for (MultipartFile file : request.files()) {
                String imageKey = minioService.uploadAsset(file);
                ItemImage image = new ItemImage();
                image.setItem(item);
                image.setImageKey(imageKey);
                image.setPosition(item.getImages().size());
                item.getImages().add(image);
            }
            return itemMapper.toResponse(itemRepository.saveAndFlush(item));
        }
        return itemMapper.toResponse(item);
    }

    @Override
    @Transactional
    public ItemResponse reorderItemImages(UUID businessId, UUID itemId, kh.edu.istad.ite.features.catalog.dto.ReorderItemImagesRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);
        
        List<UUID> orderedIds = request.imageIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID id = orderedIds.get(i);
            ItemImage img = item.getImages().stream()
                    .filter(image -> image.getId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (img != null) {
                img.setPosition(i);
            }
        }
        
        return itemMapper.toResponse(itemRepository.saveAndFlush(item));
    }

    @Override
    public Page<ItemResponse> filterItems(UUID businessId, RequestDto requestDto, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);

        org.springframework.data.jpa.domain.Specification<Item> spec = filterSpecification.getSearchSpecificationDynamic(
                requestDto.getSearchRequestDto(), requestDto.getGlobalOperator()
        );

        org.springframework.data.jpa.domain.Specification<Item> businessSpec = (root, query, cb) ->
                cb.equal(root.get("business").get("id"), businessId);

        return itemRepository.findAll(businessSpec.and(spec), pageable).map(itemMapper::toResponse);
    }

    @Override
    public ItemResponse findItemByBarcode(UUID businessId, String barcode) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = itemRepository.findByBusinessIdAndBarcode(businessId, barcode.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found with barcode: " + barcode));
        return itemMapper.toResponse(item);
    }

    private Item findItem(UUID itemId, UUID businessId) {
        return itemRepository.findByIdAndBusinessId(itemId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item has not been found"));
    }

    private ItemGroup findItemGroup(UUID itemGroupId, UUID businessId) {
        if (itemGroupId == null) {
            return null;
        }

        ItemGroup itemGroup = itemGroupRepository.findByIdAndBusinessId(itemGroupId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item group has not been found"));

        // An item is filed on a sub group, never on a top-level one. A parent
        // is a heading: what it holds is the sum of its children, so an item
        // sitting directly on it would be counted outside every child and
        // again in the parent's own total.
        if (itemGroup.getParent() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Items must be filed under a sub group, not a main item group"
            );
        }

        return itemGroup;
    }

    /**
     * A business may measure an item in a platform unit or in one of its own,
     * and in nothing else — resolving globally would let it borrow another
     * business's "Sack" and silently change meaning.
     */
    private Unit findUnit(UUID unitId, UUID businessId) {
        if (unitId == null) {
            return null;
        }

        return unitRepository.findSelectableUnit(unitId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit has not been found"));
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private List<ItemColor> mapColors(List<ItemColorRequest> requests) {
        if (requests == null) {
            return new ArrayList<>();
        }

        Set<String> seen = new HashSet<>();
        List<ItemColor> colors = new ArrayList<>();

        for (ItemColorRequest request : requests) {
            String value = TextHelper.trimToNull(request.value());

            if (value == null) continue;

            if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This item lists the colour \"" + value + "\" twice");
            }

            ItemColor color = new ItemColor();
            color.setValue(value);
            color.setColorHex(TextHelper.trimToNull(request.colorHex()));
            color.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
            colors.add(color);
        }

        return colors;
    }

    /**
     * Every variant's colour has to be one the item declares.
     *
     * A variant pointing at a colour the item does not list is a row nothing
     * can reach: the swatches are drawn from the item's colours, so no click
     * would ever select it — and it would sit there holding stock nobody can
     * sell. Two variants on the same size and colour are refused for the same
     * reason: the picker would resolve to whichever came first, silently.
     */
    private void requireDeclaredColors(Item item) {
        Set<String> declared = (item.getColors() == null ? List.<ItemColor>of() : item.getColors())
                .stream()
                .map(color -> color.getValue().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Set<String> pairs = new HashSet<>();

        for (ItemVariant variant : item.getVariants()) {
            String colorValue = variant.getColorValue();

            if (colorValue != null
                    && !declared.contains(colorValue.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + item.getName() + "\" does not come in " + colorValue);
            }

            String option = variant.getOptionName() == null
                    ? variant.getVariantName()
                    : variant.getOptionName();

            String key = (option == null ? "" : option.toLowerCase(Locale.ROOT))
                    + "|"
                    + (colorValue == null ? "" : colorValue.toLowerCase(Locale.ROOT));

            if (!pairs.add(key)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + variant.getVariantName() + "\" is listed twice");
            }
        }
    }

    private List<ItemAttribute> mapAttributes(List<ItemAttributeRequest> requests) {
        if (requests == null) {
            return new ArrayList<>();
        }
        return requests.stream().map(req -> {
            ItemAttribute attr = new ItemAttribute();
            attr.setName(req.name());
            attr.setType(req.type());
            attr.setPlacement(req.placement());
            attr.setIcon(req.icon());
            if (req.values() != null) {
                attr.setValues(req.values().stream().map(valReq -> {
                    ItemAttributeValue val = new ItemAttributeValue();
                    val.setValue(valReq.value());
                    val.setLabel(valReq.label());
                    val.setColorHex(valReq.colorHex());
                    val.setAvailable(valReq.available());
                    return val;
                }).toList());
            } else {
                attr.setValues(new ArrayList<>());
            }
            return attr;
        }).toList();
    }

    private List<DescriptionBlock> mapDescriptionBlocks(List<DescriptionBlockRequest> requests) {
        if (requests == null) {
            return new ArrayList<>();
        }
        return requests.stream().map(this::mapDescriptionBlock).toList();
    }

    private DescriptionBlock mapDescriptionBlock(DescriptionBlockRequest req) {
        DescriptionBlock block = new DescriptionBlock();
        block.setType(req.type());
        block.setText(req.text());
        block.setItems(req.items() == null ? null : new ArrayList<>(req.items()));
        block.setUrl(req.url());
        block.setCaption(req.caption());
        if (req.columns() != null) {
            block.setColumns(req.columns().stream().map(colReq -> {
                DescriptionColumn col = new DescriptionColumn();
                col.setBlocks(mapDescriptionBlocks(colReq.blocks()));
                return col;
            }).toList());
        }
        return block;
    }

    private void validateAttributes(List<ItemAttributeRequest> attributes) {
        if (attributes == null) return;
        
        Set<String> names = new HashSet<>();
        for (ItemAttributeRequest attr : attributes) {
            String nameLower = attr.name().toLowerCase();
            if (!names.add(nameLower)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name must be unique: " + attr.name());
            }
            
            if (attr.placement() == AttributePlacement.HIGHLIGHT || attr.placement() == AttributePlacement.SPECIFICATION) {
                if (attr.values() != null && attr.values().size() > 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HIGHLIGHT or SPECIFICATION attribute can have at most 1 value: " + attr.name());
                }
            }
            
            if (attr.placement() == AttributePlacement.OPTION && (attr.type() == AttributeType.SELECTION || attr.type() == AttributeType.COLOR)) {
                if (attr.values() == null || attr.values().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OPTION attribute with SELECTION or COLOR must have at least 1 value: " + attr.name());
                }
            }
            
            if (attr.type() == AttributeType.TOGGLE) {
                if (attr.values() != null && !attr.values().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOGGLE attribute must have exactly 0 values: " + attr.name());
                }
            }
            
            if (attr.values() != null) {
                for (ItemAttributeValueRequest val : attr.values()) {
                    if (attr.type() == AttributeType.COLOR && val.colorHex() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COLOR attribute values must carry colorHex: " + attr.name());
                    }
                    if (attr.type() == AttributeType.NUMBER) {
                        try {
                            Double.parseDouble(val.value());
                        } catch (NumberFormatException e) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NUMBER attribute value must parse as a number: " + val.value());
                        }
                    }
                }
            }
        }
    }

    private void validateDescriptionBlocks(List<DescriptionBlockRequest> blocks) {
        if (blocks == null) return;
        
        for (DescriptionBlockRequest block : blocks) {
            if (block.type() == DescriptionBlockType.COLUMNS) {
                if (block.columns() != null) {
                    for (DescriptionColumnRequest col : block.columns()) {
                        if (col.blocks() != null) {
                            for (DescriptionBlockRequest nestedBlock : col.blocks()) {
                                if (nestedBlock.type() == DescriptionBlockType.COLUMNS) {
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COLUMNS cannot be nested inside a column");
                                }
                                validateDescriptionBlockContent(nestedBlock);
                            }
                        }
                    }
                }
            } else {
                validateDescriptionBlockContent(block);
            }
        }
    }
    
    private void validateDescriptionBlockContent(DescriptionBlockRequest block) {
        if (block.type() == DescriptionBlockType.PARAGRAPH || block.type() == DescriptionBlockType.HEADING) {
            if (block.text() == null || block.text().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, block.type() + " block requires text");
            }
        } else if (block.type() == DescriptionBlockType.BULLETS) {
            if (block.items() == null || block.items().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BULLETS block requires items");
            }
        } else if (block.type() == DescriptionBlockType.IMAGE) {
            if (block.url() == null || block.url().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE block requires url");
            }
        }
    }

    /**
     * Brings the item's options in line with what was sent.
     *
     * An option already on the item is edited where it stands, matched by
     * name. Dropping them all and writing them again meant every save deleted
     * and re-inserted the same rows, which trips any unique index on the table
     * — Hibernate flushes its inserts first — and it also handed every option
     * a new id on every save, orphaning anything that referenced one.
     *
     * That last part matters more than it used to: an option now holds stock
     * of its own, and its stock entries point at its id.
     */
    private void replaceVariants(
            Item item,
            Business business,
            List<ItemVariantRequest> variantRequests
    ) {
        if (variantRequests == null) {
            item.getVariants().clear();
            return;
        }

        Map<String, ItemVariant> existing = new HashMap<>();

        for (ItemVariant variant : item.getVariants()) {
            if (variant.getVariantName() != null) {
                existing.putIfAbsent(variant.getVariantName().toLowerCase(), variant);
            }
        }

        Set<String> usedSlugs = new HashSet<>();
        Set<String> seen = new HashSet<>();

        for (ItemVariantRequest request : variantRequests) {
            String variantName = TextHelper.trimRequired(request.name(), "Variant name cannot be empty");
            String key = variantName.toLowerCase();

            if (!seen.add(key)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + variantName + "\" is listed twice — each option can appear only once"
                );
            }

            ItemVariant variant = existing.get(key);

            if (variant == null) {
                variant = new ItemVariant();
                variant.setBusiness(business);
                variant.setItem(item);
                variant.setVariantName(variantName);
                variant.setSlug(generateUniqueVariantSlug(variantName, usedSlugs));
                item.getVariants().add(variant);
            } else {
                // Its slug is what it was; renaming is a new option, not a
                // rewrite of this one, so the name here is only ever the same.
                variant.setVariantName(variantName);
                usedSlugs.add(variant.getSlug());
            }

            variant.setSku(TextHelper.trimToNull(request.sku()));
            variant.setBarcode(TextHelper.trimToNull(request.barcode()));
            variant.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
            // The size half falls back to the variant's own name, which is
            // what it is on an item sold by size alone.
            String optionName = TextHelper.trimToNull(request.optionName());
            variant.setOptionName(optionName == null ? variantName : optionName);
            variant.setColorValue(TextHelper.trimToNull(request.colorValue()));
            variant.setPrice(normalizePrice(request.price()));
            variant.setAvailable(request.available());
        }

        // Whatever was not sent this time has genuinely been removed.
        item.getVariants().removeIf(variant -> variant.getVariantName() == null
                || !seen.contains(variant.getVariantName().toLowerCase()));
    }

    /**
     * Attaches library add-ons to the item. Only ids this business owns are
     * accepted — an unknown one is a mistake worth reporting, not something to
     * drop quietly and leave the item missing an extra nobody notices.
     */
    private void replaceAddOns(Item item, UUID businessId, List<UUID> addOnIds) {
        if (addOnIds == null || addOnIds.isEmpty()) {
            item.getAddOns().clear();
            return;
        }

        List<UUID> wanted = addOnIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<AddOn> found = addOnRepository.findByBusinessIdAndIdIn(businessId, wanted);

        if (found.size() != wanted.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Add-on has not been found");
        }

        // Only what actually changed is written: attaching one add-on should
        // not delete and re-insert every link the item already had — and a
        // link that stays keeps whether it is currently on sale.
        Set<UUID> keep = found.stream().map(AddOn::getId).collect(Collectors.toSet());
        item.getAddOns().removeIf(link -> !keep.contains(link.getAddOn().getId()));

        Set<UUID> present = item.getAddOns().stream()
                .map(link -> link.getAddOn().getId())
                .collect(Collectors.toSet());

        for (AddOn addOn : found) {
            if (!present.contains(addOn.getId())) {
                item.getAddOns().add(new ItemAddOn(item, addOn));
            }
        }
    }

    /**
     * Turns one add-on on or off for one item.
     *
     * Off is not detached: the item still offers it, it is simply not on the
     * menu today. Losing the link would lose the setup and have to be redone.
     */
    @Override
    @Transactional
    public ItemResponse updateItemAddOnAvailability(
            UUID businessId, UUID itemId, UUID addOnId, boolean available) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);

        ItemAddOn link = item.getAddOns().stream()
                .filter(candidate -> candidate.getAddOn().getId().equals(addOnId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This item does not offer that add-on"));

        link.setAvailable(available);

        return itemMapper.toResponse(itemRepository.saveAndFlush(item));
    }

    /**
     * Brings the item's conversions in line with what was sent.
     *
     * The base unit is rejected rather than ignored: "1 g = 5 g" is not a
     * conversion anyone meant to type, and storing it would make every amount
     * on the item ambiguous.
     *
     * A conversion already on the item is edited where it stands rather than
     * dropped and written again. Only one row may exist per (item, unit,
     * option), and
     * Hibernate flushes its inserts before its deletes — so re-saving an item
     * with the same conversions would collide with the very rows it was about
     * to remove, and surface as "Item already exists". Keeping the rows also
     * keeps their ids stable for anything holding a reference to them.
     */
    private void replaceUomConversions(
            Item item,
            UUID businessId,
            List<ItemUomConversionRequest> conversionRequests
    ) {
        if (conversionRequests == null || conversionRequests.isEmpty()) {
            item.getUomConversions().clear();
            return;
        }

        UUID baseUnitId = item.getUnit() == null ? null : item.getUnit().getId();
        boolean sellsInOptions = !item.getVariants().isEmpty();
        Map<String, ItemUomConversion> byKey = new HashMap<>();
        Map<UUID, List<ItemUomConversion>> byUnit = new HashMap<>();

        for (ItemUomConversion conversion : item.getUomConversions()) {
            if (conversion.getUnit() != null) {
                byKey.put(conversionKey(conversion), conversion);
                byUnit.computeIfAbsent(conversion.getUnit().getId(), key -> new ArrayList<>())
                        .add(conversion);
            }
        }

        // By identity, because which rows survive is decided before any of them
        // has been given the option it is about to hold.
        Set<ItemUomConversion> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> seen = new HashSet<>();

        for (ItemUomConversionRequest request : conversionRequests) {
            UUID unitId = request.unitId();

            if (unitId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversion unit cannot be empty");
            }
            if (unitId.equals(baseUnitId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A conversion cannot be against the base unit"
                );
            }

            /*
             * On an item sold in options, a larger unit belongs to one of
             * them: a case of Large is not a case of Small, and the two need
             * not even hold the same number. Leaving it unsaid would define a
             * quantity for an item that is never sold as itself.
             *
             * The options were just written above, so an option typed on the
             * same screen is here to be found even though it has no id yet.
             * That is why the name is tried first: it is the one handle both
             * halves of this request share, and renaming an option makes a new
             * one, so the name is what identifies it anyway.
             */
            String wantedName = TextHelper.trimToNull(request.variantName());
            ItemVariant variant = null;

            if (wantedName != null) {
                variant = findVariantByName(item, wantedName);
            }
            if (variant == null && request.variantId() != null) {
                variant = item.getVariants().stream()
                        .filter(candidate -> request.variantId().equals(candidate.getId()))
                        .findFirst()
                        .orElse(null);
            }

            if (variant == null && (wantedName != null || request.variantId() != null)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        wantedName == null
                                ? "That option belongs to another item"
                                : "\"" + wantedName + "\" is not one of this item's options — "
                                        + "a unit can only be declared for an option listed above it"
                );
            }
            if (variant == null && sellsInOptions) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + item.getName() + "\" is sold in options — say which one this unit is for"
                );
            }

            String key = unitId + ":" + (variant == null ? "" : optionKey(variant));

            if (!seen.add(key)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each unit can be converted only once per option"
                );
            }

            ItemUomConversion conversion = byKey.get(key);

            /*
             * Failing that, any row still going spare on the same unit is
             * this one under a different option — a case that used to be for
             * nothing in particular and is now for Small.
             *
             * Reusing it is what keeps that an UPDATE. Written as a delete and
             * an insert it would collide with itself: Hibernate flushes inserts
             * before deletes, and the row it was about to remove still holds
             * this item and unit.
             */
            if (conversion == null) {
                conversion = byUnit.getOrDefault(unitId, List.of()).stream()
                        .filter(candidate -> !kept.contains(candidate))
                        .findFirst()
                        .orElse(null);
            }

            if (conversion == null) {
                conversion = new ItemUomConversion();
                conversion.setItem(item);
                conversion.setUnit(findUnit(unitId, businessId));
                item.getUomConversions().add(conversion);
            }

            kept.add(conversion);
            conversion.setVariant(variant);
            conversion.setFactor(request.factor());
            conversion.setPrice(request.price());
        }

        // Whatever was not sent this time is genuinely gone.
        item.getUomConversions().removeIf(conversion -> !kept.contains(conversion));
    }

    /** One conversion per unit per option, which is what identifies it. */
    private static String conversionKey(ItemUomConversion conversion) {
        return conversion.getUnit().getId() + ":"
                + (conversion.getVariant() == null ? "" : optionKey(conversion.getVariant()));
    }

    /**
     * How an option is told apart within its item.
     *
     * By name, because an option created in this same request has no id until
     * the flush — and because renaming one makes a new option rather than
     * editing this one, so the name is the identity either way.
     */
    private static String optionKey(ItemVariant variant) {
        if (variant.getVariantName() != null) {
            return variant.getVariantName().toLowerCase();
        }

        return variant.getId() == null ? "" : variant.getId().toString();
    }

    private static ItemVariant findVariantByName(Item item, String name) {
        String wanted = name.toLowerCase();

        return item.getVariants().stream()
                .filter(candidate -> candidate.getVariantName() != null
                        && candidate.getVariantName().toLowerCase().equals(wanted))
                .findFirst()
                .orElse(null);
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

    /**
     * What actually clashed, rather than "Item already exists" over everything.
     *
     * Only two of this table's constraints are about an item's identity; the
     * rest — a duplicate conversion, a variant — say nothing about the name,
     * and reporting them as a name clash sends people renaming an item that
     * was never the problem.
     */
    private ResponseStatusException itemConflict(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase();

        if (detail.contains("uk_items_business_name") || detail.contains("uk_items_business_slug")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Another item already uses this name. Pick a different one.",
                    e);
        }

        if (detail.contains("uk_item_uom_conversions_item_unit")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This item already has a conversion for that unit. Edit the existing one instead of adding a second.",
                    e);
        }

        // An option that has been counted or sold is referenced by its stock
        // entries and its order lines, and the database will not let it go.
        if (detail.contains("stock_entries") || detail.contains("stock_layers")
                || detail.contains("order_items")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An option on this item has stock or sales recorded against it, "
                            + "so it cannot be removed. Set it off sale instead.",
                    e);
        }

        if (detail.contains("item_variants") || detail.contains("variant")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Two options on this item have the same name. Give each one its own.",
                    e);
        }

        if (detail.contains("sku")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT, "That SKU is already used by something else.", e);
        }

        if (detail.contains("barcode")) {
            return new ResponseStatusException(
                    HttpStatus.CONFLICT, "That barcode is already used by something else.", e);
        }

        // Nothing recognised. Say so honestly and log what the database
        // actually complained about, rather than inventing a reason the shop
        // would go and act on.
        log.error("Item save rejected by the database", e);

        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The database refused this change and we could not tell why. "
                        + "Nothing was saved — please report this so it can be looked at.",
                e);
    }

    private String generateUniqueSlug(String name, UUID businessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> itemRepository.existsByBusinessIdAndSlugIgnoreCase(businessId, slug)
        );
    }

    private void ensureItemNameIsUnique(UUID businessId, String name) {
        if (itemRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item already exists");
        }
    }

    private void ensureItemNameIsUnique(UUID businessId, UUID excludedItemId, String name) {
        if (itemRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, excludedItemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item already exists");
        }
    }

    private String generateUniqueSlug(String name, UUID businessId, UUID excludedItemId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> itemRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(
                        businessId,
                        slug,
                        excludedItemId
                )
        );
    }
}
