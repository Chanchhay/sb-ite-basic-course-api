package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.AttributePlacement;
import kh.edu.istad.ite.shared.enums.AttributeType;

import java.util.List;

public record ItemAttributeRequest(
        @NotBlank(message = "attribute name cannot be empty")
        String name,

        @NotNull(message = "attribute type cannot be null")
        AttributeType type,

        AttributePlacement placement,

        @Size(max = 40, message = "attribute icon must be at most 40 characters")
        String icon,

        List<ItemAttributeValueRequest> values
) {
    public ItemAttributeRequest {
        if (placement == null) {
            placement = AttributePlacement.OPTION;
        }
    }
}
