package kh.edu.istad.ite.features.user.dto;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateUserProfileRequest(
        @Size(max = 255)
        String firstName,

        @Size(max = 255)
        String lastName,

        @Size(min = 8, max = 30)
        @Pattern(regexp = "^\\+?[0-9 ]+$", message = "Phone number may contain digits, spaces, and an optional leading plus sign.")
        String phoneNumber,

        @Pattern(regexp = "MALE|FEMALE|OTHER|UNSPECIFIED", message = "Gender must be MALE, FEMALE, OTHER, or UNSPECIFIED")
        String gender,

        String address,

        String profilePicture
) {
}
