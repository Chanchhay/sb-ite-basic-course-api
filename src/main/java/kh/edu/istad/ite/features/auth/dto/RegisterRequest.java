package kh.edu.istad.ite.features.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username cannot be empty")
        @Size(min = 3, max = 255)
        String username,

        @NotBlank(message = "password cannot be empty")
        @Size(min = 8, max = 255)
        String password,

        @NotBlank(message = "confirm password cannot be empty")
        @Size(min = 8, max = 255)
        String confirmPassword,

        @NotBlank(message = "email cannot be empty")
        @Email
        @Size(max = 255)
        String email,

        @NotBlank(message = "firstname cannot be empty")
        @Size(max = 255)
        String firstName,

        @NotBlank(message = "lastname cannot be empty")
        @Size(max = 255)
        String lastName,

        @NotBlank(message = "phone number cannot be empty")
        @Size(min = 8, max = 30)
        @Pattern(regexp = "^\\+?[0-9 ]+$", message = "Phone number may contain digits, spaces, and an optional leading plus sign.")
        String phoneNumber,

        @NotBlank(message = "gender cannot be empty")
        @Pattern(regexp = "MALE|FEMALE|OTHER|UNSPECIFIED", message = "Gender must be MALE, FEMALE, OTHER, or UNSPECIFIED")
        String gender
) {
}
