package kh.edu.istad.ite.features.user;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse updateProfile(@Valid @ModelAttribute UpdateUserProfileRequest updateUserProfileRequest) {
        return userProfileService.updateProfile(updateUserProfileRequest);
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return userProfileService.me();
    }

    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/picture")
    public void removeProfilePicture() {
        userProfileService.removeProfilePicture();
    }

}
