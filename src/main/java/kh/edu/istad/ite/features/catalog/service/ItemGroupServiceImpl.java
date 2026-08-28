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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

            /*
             * A category holding sub-categories holds no items of its own —
             * that is the rule the item form already follows, offering such a
             * category as a heading rather than a choice. Nothing enforced it
             * here, so giving a category its first sub-category left every item
             * already in it pointing at a category no screen can offer: not
             * lost, but not editable or re-filable either, and nobody told.
             */
            if (itemRepository.existsByBusinessIdAndItemGroupId(businessId, parent.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + parent.getName() + "\" already holds items, so it cannot take"
                                + " sub-categories. Move its items into a sub-category first."
                );
            }
        }

        ItemGroup itemGroup = new ItemGroup();
        itemGroup.setBusiness(business);
        itemGroup.setParent(parent);
        itemGroup.setName(TextHelper.trimRequired(request.name(), "Item group name cannot be empty"));
        itemGroup.setSlug(generateUniqueSlug(request.name(), businessId));
        itemGroup.setNote(TextHelper.trimToNull(request.note()));

        if (itemGroupRepository.existsByBusinessIdAndNameIgnoreCase(businessId, itemGroup.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item group with this name already exists");
        }

        try {
            return itemGroupMapper.toSubItemGroupResponse(itemGroupRepository.saveAndFlush(itemGroup));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item group already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemGroupResponse> findAllItemGroups(UUID businessId, Pageable pageable) {
        businessHelper.findOwnedBusiness(businessId);

        Map<UUID, List<ItemGroup>> subGroupsByParentId=
                itemGroupRepository.findByBusinessIdAndParentIsNotNullOrderByNameAsc(businessId)
                        .stream()
                        .collect(Collectors.groupingBy(itemGroup -> itemGroup.getParent().getId()));
        return itemGroupRepository.findByBusinessIdAndParentIsNull(businessId, pageable)
                .map(itemGroup -> itemGroupMapper.toItemGroupTreeResponse(
                        itemGroup,
                        subGroupsByParentId.getOrDefault(itemGroup.getId(), List.of())
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemGroupResponse> findAllItemGroupsPublic(UUID businessId) {
        // No businessHelper.findOwnedBusiness() here — the caller (public
        // storefront/menu) is never the business owner, just a shopper
        // browsing categories, so there is no "owned by the current user"
        // check to make.
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
                if (itemGroupRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, itemGroupId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Item group with this name already exists");
                }
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
            ItemGroup parent = findMainItemGroup(request.parentId(), businessId);

            // Same rule as creating one: a category that holds items cannot
            // gain sub-categories, whichever door the sub-category arrives by.
            if (itemRepository.existsByBusinessIdAndItemGroupId(businessId, parent.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "\"" + parent.getName() + "\" already holds items, so it cannot take"
                                + " sub-categories. Move its items into a sub-category first."
                );
            }

            itemGroup.setParent(parent);
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
