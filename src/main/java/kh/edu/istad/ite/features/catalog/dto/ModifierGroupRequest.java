package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ModifierGroupRequest(
        @NotBlank(message = "group name cannot be empty")
        @Size(max = 150, message = "group name must be at most 150 characters")
        String name,

        @Min(value = 0, message = "minSelect must be at least 0")
        Integer minSelect,

        @Min(value = 1, message = "maxSelect must be at least 1")
        Integer maxSelect,

        Integer sortOrder,

        @NotEmpty(message = "a group must have at least one option")
        List<@Valid ModifierOptionRequest> options
) {
}