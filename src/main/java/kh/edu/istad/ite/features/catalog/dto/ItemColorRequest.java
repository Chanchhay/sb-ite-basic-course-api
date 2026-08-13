package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ItemColorRequest(
        @NotBlank(message = "a colour needs a name")
        @Size(max = 150, message = "a colour name must be at most 150 characters")
        String value,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "colorHex must be a valid hex color code")
        String colorHex,

        /** Already uploaded: the client sends the URL the asset store gave back. */
        @Size(max = 500, message = "a colour image URL must be at most 500 characters")
        String imageUrl
) {
}
