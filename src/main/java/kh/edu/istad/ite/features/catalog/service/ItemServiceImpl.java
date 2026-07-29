package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.shared.enums.ItemStatus;
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
public class ItemServiceImpl implements ItemService {

    private static final int SLUG_MAX_LENGTH = 250;
    private static final int VARIANT_SLUG_MAX_LENGTH = 255;
    private static final String SLUG_FALLBACK = "item";
    private static final String VARIANT_SLUG_FALLBACK = "variant";
    private static final int DEFAULT_LOW_STOCK = 20;

    private final BusinessHelper businessHelper;
    private final ItemGroupRepository itemGroupRepository;
    private final UnitRepository unitRepository;
    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;

    @Override
    @Transactional
    public ItemResponse createItem(UUID businessId, CreateItemRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        Item item = new Item();
        item.setBusiness(business);
        item.setItemGroup(findItemGroup(request.itemGroupId(), businessId));
        item.setUnit(findUnit(request.unitId()));
        String name = TextHelper.trimRequired(request.name(), "Item name cannot be empty");
        ensureItemNameIsUnique(businessId, name);
        item.setName(name);
        item.setSlug(generateUniqueSlug(name, businessId));
        item.setSku(TextHelper.trimToNull(request.sku()));
        item.setCode(TextHelper.trimToNull(request.code()));
        item.setDescription(TextHelper.trimToNull(request.description()));
        item.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
        item.setBarcode(TextHelper.trimToNull(request.barcode()));
        item.setPrice(normalizePrice(request.price()));
        item.setItemType(request.itemType());
        item.setAttributes(mapAttributes(request.attributes()));
        replaceVariants(item, business, request.variants());
        item.setLowStockDefault(request.lowStockDefault() == null ? DEFAULT_LOW_STOCK : request.lowStockDefault());
        item.setStatus(request.status() == null ? ItemStatus.ACTIVE : request.status());

        try {
            return itemMapper.toResponse(itemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item already exists", e);
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
    public ItemResponse updateItem(UUID businessId, UUID itemId, UpdateItemRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);

        if (request.itemGroupId() != null) {
            item.setItemGroup(findItemGroup(request.itemGroupId(), businessId));
        }
        if (request.unitId() != null) {
            item.setUnit(findUnit(request.unitId()));
        }
        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Item name cannot be empty");
            if (!name.equals(item.getName())) {
                ensureItemNameIsUnique(businessId, itemId, name);
                item.setName(name);
                item.setSlug(generateUniqueSlug(name, businessId, itemId));
            }
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
        if (request.attributes() != null) {
            item.setAttributes(mapAttributes(request.attributes()));
        }
        if (request.variants() != null) {
            replaceVariants(item, item.getBusiness(), request.variants());
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteItem(UUID businessId, UUID itemId) {
        businessHelper.findOwnedBusiness(businessId);
        Item item = findItem(itemId, businessId);

        itemRepository.delete(item);
        itemRepository.flush();
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

        if (itemGroupRepository.existsByBusinessIdAndParentId(businessId, itemGroupId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Item group with sub groups cannot be used for items"
            );
        }

        return itemGroup;
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

    private List<kh.edu.istad.ite.features.catalog.entity.ItemAttribute> mapAttributes(List<kh.edu.istad.ite.features.catalog.dto.ItemAttributeRequest> requests) {
        if (requests == null) {
            return null;
        }
        return requests.stream().map(req -> {
            kh.edu.istad.ite.features.catalog.entity.ItemAttribute attr = new kh.edu.istad.ite.features.catalog.entity.ItemAttribute();
            attr.setName(req.name());
            attr.setType(req.type());
            attr.setValues(req.values());
            return attr;
        }).toList();
    }

    private void replaceVariants(
            Item item,
            Business business,
            List<ItemVariantRequest> variantRequests
    ) {
        item.getVariants().clear();
        if (variantRequests == null) {
            return;
        }

        Set<String> usedSlugs = new HashSet<>();
        for (ItemVariantRequest request : variantRequests) {
            String variantName = TextHelper.trimRequired(request.name(), "Variant name cannot be empty");

            ItemVariant variant = new ItemVariant();
            variant.setBusiness(business);
            variant.setItem(item);
            variant.setVariantName(variantName);
            variant.setSlug(generateUniqueVariantSlug(variantName, usedSlugs));
            variant.setPrice(normalizePrice(request.price()));
            item.getVariants().add(variant);
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
