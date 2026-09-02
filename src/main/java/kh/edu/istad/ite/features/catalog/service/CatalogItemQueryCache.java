package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.config.CacheNames;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The cached read side of the catalogue.
 *
 * Every lookup here is blind to the recycle bin. The filter belongs on this
 * side of the cache rather than above it: what gets stored under a key has to
 * already be the answer, or a trashed item stays served from Redis long after
 * it was deleted. {@link kh.edu.istad.ite.shared.cache.BusinessCacheEvictor}
 * is what puts a delete, a restore, or a purge through to these keys.
 */
@Component
@RequiredArgsConstructor
class CatalogItemQueryCache {

    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATALOG_ITEMS, key = "T(kh.edu.istad.ite.config.CacheKeys).page(#p0, #p1)")
    public PageResponse<ItemResponse> findAllItems(UUID businessId, Pageable pageable) {
        Page<Item> items = itemRepository.findAllByBusinessIdAndIsDeletedFalse(businessId, pageable);
        return PageResponse.from(items.map(itemMapper::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATALOG_ITEM_BY_ID, key = "T(kh.edu.istad.ite.config.CacheKeys).item(#p0, #p1)")
    public ItemResponse findItemById(UUID businessId, UUID itemId) {
        return itemMapper.toResponse(itemRepository.findByIdAndBusinessIdAndIsDeletedFalse(itemId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item has not been found")));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATALOG_ITEM_BY_BARCODE, key = "T(kh.edu.istad.ite.config.CacheKeys).barcode(#p0, #p1)")
    public ItemResponse findItemByBarcode(UUID businessId, String barcode) {
        return itemRepository.findByBusinessIdAndBarcodeAndIsDeletedFalse(businessId, barcode.trim())
                .map(itemMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found with barcode: " + barcode));
    }
}
