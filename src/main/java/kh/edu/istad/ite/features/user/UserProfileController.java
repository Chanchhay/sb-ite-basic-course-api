package kh.edu.istad.ite.features.user;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserProfileResponse updateProfile(@Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {
        return userProfileService.updateProfile(updateUserProfileRequest);
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return userProfileService.me();
    }

    @PostMapping(value = "/me/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        return userProfileService.uploadProfilePicture(file);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/picture")
    public void removeProfilePicture() {
        userProfileService.removeProfilePicture();
    }
}
