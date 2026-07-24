package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SocialLinkRequest(
        @NotBlank(message = "platform cannot be empty")
        @Size(max = 50, message = "platform must be at most 50 characters")
        String platform,

        @NotBlank(message = "url cannot be empty")
        @Size(max = 255, message = "url must be at most 255 characters")
        @Pattern(
                regexp = "^https?://.+$",
                message = "url must start with http:// or https://"
        )
        String url
) {
}
