package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.ModifierGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ModifierGroupResponse;

import java.util.List;
import java.util.UUID;

public interface ModifierGroupService {

    ModifierGroupResponse createGroup(UUID businessId, UUID itemId, ModifierGroupRequest request);

    List<ModifierGroupResponse> findAllGroups(UUID businessId, UUID itemId);

    ModifierGroupResponse findGroupById(UUID businessId, UUID itemId, UUID groupId);

    ModifierGroupResponse updateGroup(UUID businessId, UUID itemId, UUID groupId, ModifierGroupRequest request);

    void deleteGroup(UUID businessId, UUID itemId, UUID groupId);
}