package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OptionPresetValueRequest(
        @NotBlank(message = "a preset value cannot be empty")
        @Size(max = 150, message = "a preset value must be at most 150 characters")
        String value,

        @Size(max = 20, message = "colorHex must be at most 20 characters")
        String colorHex,

        @Size(max = 255, message = "imageUrl must be at most 255 characters")
        String imageUrl
) {
}
