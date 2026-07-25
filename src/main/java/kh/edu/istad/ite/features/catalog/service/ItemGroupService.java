package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemSubGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemGroupRequest;

import java.util.List;
import java.util.UUID;

public interface ItemGroupService {

    ItemSubGroupResponse createItemGroup(UUID businessId, CreateItemGroupRequest request);

    List<ItemGroupResponse> findAllItemGroups(UUID businessId);

    ItemSubGroupResponse updateItemGroup(UUID businessId, UUID itemGroupId, UpdateItemGroupRequest request);

    void deleteItemGroup(UUID businessId, UUID itemGroupId);
}
