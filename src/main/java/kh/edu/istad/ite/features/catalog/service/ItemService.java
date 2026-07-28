package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;

import java.util.List;
import java.util.UUID;

public interface ItemService {

    ItemResponse createItem(UUID businessId, CreateItemRequest request);

    List<ItemResponse> findAllItems(UUID businessId);

    ItemResponse findItemById(UUID businessId, UUID itemId);

    ItemResponse updateItem(UUID businessId, UUID itemId, UpdateItemRequest request);

    void deleteItem(UUID businessId, UUID itemId);

    ItemResponse findItemByBarcode(UUID businessId, String barcode);
}
