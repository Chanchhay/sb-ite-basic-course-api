package kh.edu.istad.ite.features.catalog.dto;

import java.util.List;
import java.util.UUID;

public record ModifierGroupResponse(
        UUID id,
        UUID itemId,
        String name,
        Integer minSelect,
        Integer maxSelect,
        Integer sortOrder,
        List<ModifierOptionResponse> options
) {
}