package kh.edu.istad.ite.features.user.dto;
import lombok.Builder;

@Builder
public record UserProfileResponse(
        String userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String gender,
        String role,
        String address,
        String profilePicture
) {
}
