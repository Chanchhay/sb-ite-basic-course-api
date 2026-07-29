package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.AttributePlacement;
import kh.edu.istad.ite.shared.enums.AttributeType;

import java.util.List;

public record ItemAttributeResponse(
        String name,
        AttributeType type,
        AttributePlacement placement,
        String icon,
        List<ItemAttributeValueResponse> values
) {
}
