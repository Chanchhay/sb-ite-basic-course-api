package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemSubGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemGroupRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ItemGroupService {

    ItemSubGroupResponse createItemGroup(UUID businessId, CreateItemGroupRequest request);

    Page<ItemGroupResponse> findAllItemGroups(UUID businessId, Pageable pageable);

    /** Same tree shape as {@link #findAllItemGroups}, but for the public storefront — no ownership check, no paging. */
    List<ItemGroupResponse> findAllItemGroupsPublic(UUID businessId);

    ItemSubGroupResponse updateItemGroup(UUID businessId, UUID itemGroupId, UpdateItemGroupRequest request);

    void deleteItemGroup(UUID businessId, UUID itemGroupId);
}
