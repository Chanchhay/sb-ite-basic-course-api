package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ReorderItemImagesRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.UploadItemImagesRequest;

import java.util.List;
import java.util.UUID;

public interface ItemService {

    ItemResponse createItem(UUID businessId, CreateItemRequest request);

    ItemResponse uploadItemImages(UUID businessId, UUID itemId, UploadItemImagesRequest request);

    ItemResponse reorderItemImages(UUID businessId, UUID itemId, ReorderItemImagesRequest request);

    ItemResponse deleteItemImage(UUID businessId, UUID itemId, UUID imageId);

    List<ItemResponse> findAllItems(UUID businessId);

    ItemResponse findItemById(UUID businessId, UUID itemId);

    ItemResponse updateItem(UUID businessId, UUID itemId, UpdateItemRequest request);

    void deleteItem(UUID businessId, UUID itemId);

    ItemResponse findItemByBarcode(UUID businessId, String barcode);
}
