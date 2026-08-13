package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.AddOnSelectionRule;

import java.util.List;
import java.util.UUID;

public record AddOnSetRequest(
        @NotBlank(message = "set name cannot be empty")
        @Size(max = 150, message = "set name must be at most 150 characters")
        String name,

        AddOnSelectionRule rule,

        @Min(value = 1, message = "maxChoices must be at least 1")
        Integer maxChoices,

        Boolean required,

        @NotEmpty(message = "a set needs at least one add-on")
        List<UUID> addOnIds
) {
    public AddOnSetRequest {
        if (rule == null) {
            rule = AddOnSelectionRule.ANY;
        }
        if (required == null) {
            required = false;
        }
        // A ceiling only means something when there is one to hit.
        if (rule == AddOnSelectionRule.ANY) {
            maxChoices = null;
        }
    }
}
