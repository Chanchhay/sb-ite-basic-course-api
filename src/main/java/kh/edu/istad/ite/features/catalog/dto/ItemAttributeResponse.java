package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.AttributeType;

import java.util.List;

public record ItemAttributeResponse(
        String name,
        AttributeType type,
        List<String> values
) {
}
