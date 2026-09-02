package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.config.CacheNames;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.mapper.ItemGroupMapper;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ItemGroupQueryCache {

    private final ItemGroupRepository itemGroupRepository;
    private final ItemGroupMapper itemGroupMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATALOG_ITEM_GROUPS, key = "T(kh.edu.istad.ite.config.CacheKeys).page(#p0, #p1)")
    public PageResponse<ItemGroupResponse> findAllItemGroups(UUID businessId, Pageable pageable) {
        Map<UUID, List<ItemGroup>> subGroupsByParentId =
                itemGroupRepository.findByBusinessIdAndParentIsNotNullOrderByNameAsc(businessId)
                        .stream()
                        .collect(Collectors.groupingBy(itemGroup -> itemGroup.getParent().getId()));

        return PageResponse.from(itemGroupRepository.findByBusinessIdAndParentIsNull(businessId, pageable)
                .map(itemGroup -> itemGroupMapper.toItemGroupTreeResponse(
                        itemGroup,
                        subGroupsByParentId.getOrDefault(itemGroup.getId(), List.of())
                )));
    }

    /**
     * Deliberately uncached. {@code StorefrontServiceImpl#getPublicStoreItemGroups} is
     * the only caller and already caches this under {@code PUBLIC_STORE_ITEM_GROUPS},
     * keyed by storefront slug. Caching here too filed a second copy of the same menu
     * in the same cache under a business id, so every entry was stored twice.
     */
    @Transactional(readOnly = true)
    public List<ItemGroupResponse> findAllItemGroupsPublic(UUID businessId) {
        Map<UUID, List<ItemGroup>> subGroupsByParentId =
                itemGroupRepository.findByBusinessIdAndParentIsNotNullOrderByNameAsc(businessId)
                        .stream()
                        .collect(Collectors.groupingBy(itemGroup -> itemGroup.getParent().getId()));

        return itemGroupRepository.findByBusinessIdAndParentIsNullOrderByNameAsc(businessId)
                .stream()
                .map(itemGroup -> itemGroupMapper.toItemGroupTreeResponse(
                        itemGroup,
                        subGroupsByParentId.getOrDefault(itemGroup.getId(), List.of())
                ))
                .toList();
    }
}
