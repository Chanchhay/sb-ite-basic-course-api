package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ReorderItemImagesRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.UploadItemImagesRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ItemService {

    ItemResponse createItem(UUID businessId, CreateItemRequest request, List<MultipartFile> files);

    ItemResponse uploadItemImages(UUID businessId, UUID itemId, UploadItemImagesRequest request);

    ItemResponse reorderItemImages(UUID businessId, UUID itemId, ReorderItemImagesRequest request);

    ItemResponse deleteItemImage(UUID businessId, UUID itemId, UUID imageId);

    List<ItemResponse> findAllItems(UUID businessId);

    ItemResponse findItemById(UUID businessId, UUID itemId);

    ItemResponse updateItem(UUID businessId, UUID itemId, UpdateItemRequest request, List<MultipartFile> files);

    /**
     * Changes only which add-ons the item offers.
     *
     * A narrow write on purpose: attaching one from a list row must not be
     * able to touch the item's name, images or anything else it never saw.
     */
    ItemResponse updateItemAddOns(UUID businessId, UUID itemId, List<UUID> addOnIds);

    /** Turns one add-on on or off for one item, without unlinking it. */
    ItemResponse updateItemAddOnAvailability(UUID businessId, UUID itemId, UUID addOnId, boolean available);

    void deleteItem(UUID businessId, UUID itemId);

    ItemResponse findItemByBarcode(UUID businessId, String barcode);
    
    org.springframework.data.domain.Page<ItemResponse> filterItems(UUID businessId, kh.edu.istad.ite.config.filter.RequestDto requestDto, org.springframework.data.domain.Pageable pageable);
}
