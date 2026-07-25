package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemSubGroupResponse;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemGroupMapper {

    public ItemSubGroupResponse toSubItemGroupResponse(ItemGroup itemGroup) {
        if (itemGroup == null) {
            return null;
        }

        return new ItemSubGroupResponse(
                itemGroup.getId(),
                itemGroup.getName(),
                itemGroup.getSlug(),
                itemGroup.getNote(),
                itemGroup.getParent() == null ? null : itemGroup.getParent().getId()
        );
    }

    public ItemGroupResponse toItemGroupTreeResponse(
            ItemGroup itemGroup,
            List<ItemGroup> subGroups
    ) {
        return new ItemGroupResponse(
                itemGroup.getId(),
                itemGroup.getName(),
                itemGroup.getSlug(),
                itemGroup.getNote(),
                subGroups.stream()
                        .map(this::toSubItemGroupResponse)
                        .toList()
        );
    }
}
