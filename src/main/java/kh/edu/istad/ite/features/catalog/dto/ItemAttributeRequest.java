package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.AttributeType;

import java.util.List;

public record ItemAttributeRequest(
        @NotBlank(message = "attribute name cannot be empty")
        String name,

        @NotNull(message = "attribute type cannot be null")
        AttributeType type,

        List<String> values
) {
}
