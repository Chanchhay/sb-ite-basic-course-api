package kh.edu.istad.ite.features.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonDeserialize(using = ItemAttributeValueRequestDeserializer.class)
public record ItemAttributeValueRequest(
        @NotBlank(message = "attribute value cannot be empty")
        @Size(min = 1, max = 150, message = "attribute value must be between 1 and 150 characters")
        String value,

        @Size(max = 150, message = "attribute label must be at most 150 characters")
        String label,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "colorHex must be a valid hex color code")
        String colorHex,

        Boolean available
) {
}
