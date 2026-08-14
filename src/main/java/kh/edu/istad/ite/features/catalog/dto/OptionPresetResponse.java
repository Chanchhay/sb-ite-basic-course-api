package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.AttributeType;

import java.util.List;
import java.util.UUID;

public record OptionPresetResponse(
        UUID id,
        String name,
        AttributeType type,
        Boolean required,
        List<OptionPresetValueResponse> values
) {
}
