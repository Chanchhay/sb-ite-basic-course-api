package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemAddOnAvailabilityRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemAddOnsRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ReorderItemImagesRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.UploadItemImagesRequest;
import kh.edu.istad.ite.features.catalog.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import kh.edu.istad.ite.shared.enums.ItemType;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.features.catalog.dto.ItemAttributeRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemColorRequest;
import kh.edu.istad.ite.features.catalog.dto.DescriptionBlockRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemUomConversionRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final jakarta.validation.Validator validator;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse createItem(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID itemGroupId,
            @RequestParam(required = false) UUID unitId,
            @RequestParam String name,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) List<String> images,
            @RequestParam(required = false) String badge,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) BigDecimal compareAtPrice,
            @RequestParam ItemType itemType,
            @RequestParam(required = false) Boolean trackInventory,
            @RequestPart(required = false) List<ItemAttributeRequest> attributes,
            @RequestPart(required = false) List<ItemColorRequest> colors,
            @RequestPart(required = false) List<DescriptionBlockRequest> descriptionBlocks,
            @RequestPart(required = false) List<ItemVariantRequest> variants,
            @RequestPart(required = false) List<UUID> addOnIds,
            @RequestPart(required = false) List<ItemUomConversionRequest> uomConversions,
            @RequestParam(required = false) Integer lowStockDefault,
            @RequestParam(required = false) ItemStatus status,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        CreateItemRequest request = new CreateItemRequest(
                itemGroupId, unitId, name, sku, code, description, imageUrl, images,
                badge, barcode, price, compareAtPrice, itemType, trackInventory, attributes, colors,
                descriptionBlocks, variants, addOnIds, uomConversions, lowStockDefault, status
        );
        java.util.Set<jakarta.validation.ConstraintViolation<CreateItemRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new jakarta.validation.ConstraintViolationException(violations);
        }
        return itemService.createItem(businessId, request, files);
    }

    @GetMapping
    public List<ItemResponse> findAllItems(@PathVariable UUID businessId) {
        return itemService.findAllItems(businessId);
    }

    @GetMapping("/{itemId}")
    public ItemResponse findItemById(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId
    ) {
        return itemService.findItemById(businessId, itemId);
    }

    @PutMapping(value = "/{itemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse updateItem(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @RequestParam(required = false) UUID itemGroupId,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) List<String> images,
            @RequestParam(required = false) String badge,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) BigDecimal compareAtPrice,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) Boolean trackInventory,
            @RequestPart(required = false) List<ItemAttributeRequest> attributes,
            @RequestPart(required = false) List<ItemColorRequest> colors,
            @RequestPart(required = false) List<DescriptionBlockRequest> descriptionBlocks,
            @RequestPart(required = false) List<ItemVariantRequest> variants,
            @RequestPart(required = false) List<UUID> addOnIds,
            @RequestPart(required = false) List<ItemUomConversionRequest> uomConversions,
            @RequestParam(required = false) Integer lowStockDefault,
            @RequestParam(required = false) ItemStatus status,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        UpdateItemRequest request = new UpdateItemRequest(
                itemGroupId, unitId, name, sku, code, description, imageUrl, images,
                badge, barcode, price, compareAtPrice, itemType, trackInventory, attributes, colors,
                descriptionBlocks, variants, addOnIds, uomConversions, lowStockDefault, status
        );
        java.util.Set<jakarta.validation.ConstraintViolation<UpdateItemRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new jakarta.validation.ConstraintViolationException(violations);
        }
        return itemService.updateItem(businessId, itemId, request, files);
    }

    @PostMapping(value = "/{itemId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse uploadItemImages(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @ModelAttribute UploadItemImagesRequest request
    ) {
        return itemService.uploadItemImages(businessId, itemId, request);
    }

    @PutMapping("/{itemId}/images/order")
    public ItemResponse reorderItemImages(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ReorderItemImagesRequest request
    ) {
        return itemService.reorderItemImages(businessId, itemId, request);
    }

    @DeleteMapping("/{itemId}/images/{imageId}")
    public ItemResponse deleteItemImage(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PathVariable UUID imageId
    ) {
        return itemService.deleteItemImage(businessId, itemId, imageId);
    }

    /** Attach or detach add-ons without touching the rest of the item. */
    @PutMapping("/{itemId}/add-ons")
    public ItemResponse updateItemAddOns(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemAddOnsRequest request
    ) {
        return itemService.updateItemAddOns(businessId, itemId, request.addOnIds());
    }

    /** Take one add-on off this item's menu, or put it back, without unlinking. */
    @PutMapping("/{itemId}/add-ons/{addOnId}")
    public ItemResponse updateItemAddOnAvailability(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PathVariable UUID addOnId,
            @Valid @RequestBody ItemAddOnAvailabilityRequest request
    ) {
        return itemService.updateItemAddOnAvailability(
                businessId, itemId, addOnId, request.available());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{itemId}")
    public void deleteItem(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId
    ) {
        itemService.deleteItem(businessId, itemId);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/barcode/{barcode}")
    public ItemResponse findItemByBarcode(
            @PathVariable UUID businessId,
            @PathVariable String barcode
    ) {
        return itemService.findItemByBarcode(businessId, barcode);
    }

    @PostMapping("/filter")
    public org.springframework.data.domain.Page<ItemResponse> filterItems(
            @PathVariable UUID businessId,
            @RequestBody kh.edu.istad.ite.config.filter.RequestDto request,
            org.springframework.data.domain.Pageable pageable
    ) {
        return itemService.filterItems(businessId, request, pageable);
    }
}
