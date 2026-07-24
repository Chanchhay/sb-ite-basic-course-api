package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBusinessRequest(
        @NotBlank(message = "name cannot be empty")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotBlank(message = "categoryId cannot be empty")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "categoryId must be a valid UUID"
        )
        String categoryId,

        @NotBlank(message = "email cannot be empty")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "address cannot be empty")
        @Size(max = 255, message = "address must be at most 255 characters")
        String address
) {
}
