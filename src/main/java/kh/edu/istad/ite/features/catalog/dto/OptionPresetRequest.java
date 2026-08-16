package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.AttributeType;

import java.util.List;

public record OptionPresetRequest(
        @NotBlank(message = "preset name cannot be empty")
        @Size(max = 150, message = "preset name must be at most 150 characters")
        String name,

        AttributeType type,

        Boolean required,

        @NotEmpty(message = "a preset needs at least one value")
        @Size(max = 50, message = "a preset can hold at most 50 values")
        List<@Valid OptionPresetValueRequest> values
) {
    public OptionPresetRequest {
        // A preset fills in a list of choices, so those are the only two types
        // that mean anything here.
        if (type == null || (type != AttributeType.SELECTION && type != AttributeType.COLOR)) {
            type = AttributeType.SELECTION;
        }
        if (required == null) {
            required = true;
        }
    }
}
