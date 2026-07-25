package kh.edu.istad.ite.features.catalog.dto;

import java.util.List;
import java.util.UUID;

public record ItemGroupResponse(
        UUID id,
        String name,
        String slug,
        String note,
        List<ItemSubGroupResponse> subGroups
) {
}
