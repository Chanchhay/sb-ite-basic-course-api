package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemSubGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.mapper.ItemGroupMapper;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemGroupServiceImpl implements ItemGroupService {

    private static final int SLUG_MAX_LENGTH = 200;
    private static final String SLUG_FALLBACK = "item-group";

    private final BusinessHelper businessHelper;
    private final ItemGroupRepository itemGroupRepository;
    private final ItemRepository itemRepository;
    private final ItemGroupMapper itemGroupMapper;

    @Override
    @Transactional
    public ItemSubGroupResponse createItemGroup(UUID businessId, CreateItemGroupRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        ItemGroup parent = null;

        if (request.parentId() != null) {
            parent = itemGroupRepository.findByIdAndBusinessId(request.parentId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Parent item group has not been found"
                    ));

            if (parent.getParent() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Item groups support only 2 levels"
                );
            }
        }

        ItemGroup itemGroup = new ItemGroup();
        itemGroup.setBusiness(business);
        itemGroup.setParent(parent);
        itemGroup.setName(TextHelper.trimRequired(request.name(), "Item group name cannot be empty"));
        itemGroup.setSlug(generateUniqueSlug(request.name(), businessId));
        itemGroup.setNote(TextHelper.trimToNull(request.note()));

        try {
            return itemGroupMapper.toSubItemGroupResponse(itemGroupRepository.saveAndFlush(itemGroup));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item group already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemGroupResponse> findAllItemGroups(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);

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

    @Override
    @Transactional
    public ItemSubGroupResponse updateItemGroup(
            UUID businessId,
            UUID itemGroupId,
            UpdateItemGroupRequest request
    ) {
        businessHelper.findOwnedBusiness(businessId);
        ItemGroup itemGroup = findItemGroup(itemGroupId, businessId);

        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Item group name cannot be empty");
            if (!name.equals(itemGroup.getName())) {
                itemGroup.setName(name);
                itemGroup.setSlug(generateUniqueSlug(name, businessId, itemGroupId));
            }
        }

        if (request.note() != null) {
            itemGroup.setNote(TextHelper.trimToNull(request.note()));
        }

        if (request.parentId() != null) {
            if (itemGroupId.equals(request.parentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item group cannot be its own parent");
            }
            if (itemGroupRepository.existsByBusinessIdAndParentId(businessId, itemGroupId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Item group with sub groups cannot become a sub group"
                );
            }
            itemGroup.setParent(findMainItemGroup(request.parentId(), businessId));
        }

        try {
            return itemGroupMapper.toSubItemGroupResponse(itemGroupRepository.saveAndFlush(itemGroup));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item group already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteItemGroup(UUID businessId, UUID itemGroupId) {
        businessHelper.findOwnedBusiness(businessId);
        ItemGroup itemGroup = findItemGroup(itemGroupId, businessId);

        if (itemGroupRepository.existsByBusinessIdAndParentId(businessId, itemGroupId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete item group with sub groups"
            );
        }
        if (itemRepository.existsByBusinessIdAndItemGroupId(businessId, itemGroupId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete item group that is used by items"
            );
        }

        itemGroupRepository.delete(itemGroup);
        itemGroupRepository.flush();
    }

    private String generateUniqueSlug(String name, UUID businessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> itemGroupRepository.existsByBusinessIdAndSlugIgnoreCase(businessId, slug)
        );
    }

    private String generateUniqueSlug(String name, UUID businessId, UUID excludedItemGroupId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> itemGroupRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(
                        businessId,
                        slug,
                        excludedItemGroupId
                )
        );
    }

    private ItemGroup findItemGroup(UUID itemGroupId, UUID businessId) {
        return itemGroupRepository.findByIdAndBusinessId(itemGroupId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item group has not been found"));
    }

    private ItemGroup findMainItemGroup(UUID itemGroupId, UUID businessId) {
        ItemGroup itemGroup = findItemGroup(itemGroupId, businessId);
        if (itemGroup.getParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent item group must be a main item group");
        }

        return itemGroup;
    }
}
