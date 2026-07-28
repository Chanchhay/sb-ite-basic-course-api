package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.ModifierGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ModifierOptionResponse;
import kh.edu.istad.ite.features.catalog.entity.ModifierGroup;
import kh.edu.istad.ite.features.catalog.entity.ModifierOption;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModifierGroupMapper {

    public ModifierGroupResponse toResponse(ModifierGroup group) {
        List<ModifierOptionResponse> options = group.getOptions().stream()
                .map(this::toOptionResponse)
                .toList();

        return new ModifierGroupResponse(
                group.getId(),
                group.getItem().getId(),
                group.getName(),
                group.getMinSelect(),
                group.getMaxSelect(),
                group.getSortOrder(),
                options
        );
    }

    private ModifierOptionResponse toOptionResponse(ModifierOption option) {
        return new ModifierOptionResponse(
                option.getId(),
                option.getName(),
                option.getPrice(),
                option.getIsDefault(),
                option.getSortOrder()
        );
    }
}