package kh.edu.istad.ite.features.catalog.dto;

import java.util.UUID;

public record ItemSubGroupResponse(
        UUID id,
        String name,
        String slug,
        String note,
        UUID parentId
) {
}
