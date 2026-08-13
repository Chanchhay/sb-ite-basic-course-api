package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.AddOnSelectionRule;

import java.util.List;
import java.util.UUID;

public record AddOnSetResponse(
        UUID id,
        String name,
        AddOnSelectionRule rule,
        Integer maxChoices,
        Boolean required,
        List<AddOnResponse> addOns
) {
}
