package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.ModifierGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ModifierGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ModifierOptionRequest;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ModifierGroup;
import kh.edu.istad.ite.features.catalog.entity.ModifierOption;
import kh.edu.istad.ite.features.catalog.mapper.ModifierGroupMapper;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.ModifierGroupRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModifierGroupServiceImpl implements ModifierGroupService {

    private final BusinessHelper businessHelper;
    private final ItemRepository itemRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierGroupMapper modifierGroupMapper;

    @Override
    @Transactional
    public ModifierGroupResponse createGroup(UUID businessId, UUID itemId, ModifierGroupRequest request) {
        Business business = businessHelper.findAccessibleBusiness(businessId);
        Item item = requireItem(businessId, itemId);

        if (modifierGroupRepository.existsByItemIdAndBusinessIdAndNameIgnoreCase(
                itemId, businessId, request.name().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This item already has a modifier group named " + request.name());
        }

        ModifierGroup group = new ModifierGroup();
        group.setBusiness(business);
        group.setItem(item);
        applyRequest(group, request);

        return modifierGroupMapper.toResponse(modifierGroupRepository.save(group));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> findAllGroups(UUID businessId, UUID itemId) {
        businessHelper.findAccessibleBusiness(businessId);
        requireItem(businessId, itemId);

        return modifierGroupRepository
                .findAllByItemIdAndBusinessIdOrderBySortOrderAsc(itemId, businessId)
                .stream()
                .map(modifierGroupMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ModifierGroupResponse findGroupById(UUID businessId, UUID itemId, UUID groupId) {
        businessHelper.findAccessibleBusiness(businessId);
        return modifierGroupMapper.toResponse(requireGroup(businessId, itemId, groupId));
    }

    @Override
    @Transactional
    public ModifierGroupResponse updateGroup(
            UUID businessId, UUID itemId, UUID groupId, ModifierGroupRequest request) {
        businessHelper.findAccessibleBusiness(businessId);
        ModifierGroup group = requireGroup(businessId, itemId, groupId);

        if (modifierGroupRepository.existsByItemIdAndBusinessIdAndNameIgnoreCaseAndIdNot(
                itemId, businessId, request.name().trim(), groupId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This item already has a modifier group named " + request.name());
        }

        // Full replace of the option set; orphanRemoval deletes the old rows.
        group.getOptions().clear();
        applyRequest(group, request);

        return modifierGroupMapper.toResponse(modifierGroupRepository.save(group));
    }

    @Override
    @Transactional
    public void deleteGroup(UUID businessId, UUID itemId, UUID groupId) {
        businessHelper.findAccessibleBusiness(businessId);
        ModifierGroup group = requireGroup(businessId, itemId, groupId);
        modifierGroupRepository.delete(group);
    }

    /** Sets scalar fields and rebuilds the option list from the request, with sanity checks. */
    private void applyRequest(ModifierGroup group, ModifierGroupRequest request) {
        int minSelect = request.minSelect() == null ? 0 : request.minSelect();
        Integer maxSelect = request.maxSelect();
        int optionCount = request.options().size();

        if (maxSelect != null && maxSelect < minSelect) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "maxSelect cannot be smaller than minSelect");
        }
        if (minSelect > optionCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "minSelect cannot exceed the number of options");
        }

        group.setName(request.name().trim());
        group.setMinSelect(minSelect);
        group.setMaxSelect(maxSelect);
        group.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());

        int index = 0;
        for (ModifierOptionRequest optionRequest : request.options()) {
            ModifierOption option = new ModifierOption();
            option.setName(optionRequest.name().trim());
            option.setPrice(optionRequest.price() == null ? BigDecimal.ZERO : optionRequest.price());
            option.setIsDefault(Boolean.TRUE.equals(optionRequest.isDefault()));
            option.setSortOrder(optionRequest.sortOrder() == null ? index : optionRequest.sortOrder());
            group.addOption(option);
            index++;
        }
    }

    private Item requireItem(UUID businessId, UUID itemId) {
        return itemRepository.findByIdAndBusinessId(itemId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item has not been found: " + itemId));
    }

    private ModifierGroup requireGroup(UUID businessId, UUID itemId, UUID groupId) {
        return modifierGroupRepository.findByIdAndItemIdAndBusinessId(groupId, itemId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Modifier group has not been found: " + groupId));
    }
}