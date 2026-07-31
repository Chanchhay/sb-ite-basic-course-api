package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
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

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse createItem(
            @PathVariable UUID businessId,
            @Valid @ModelAttribute CreateItemRequest request
    ) {
        return itemService.createItem(businessId, request);
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
            @Valid @ModelAttribute UpdateItemRequest request
    ) {
        return itemService.updateItem(businessId, itemId, request);
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
}
