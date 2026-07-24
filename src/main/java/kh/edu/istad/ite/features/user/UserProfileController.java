package kh.edu.istad.ite.features.user;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PatchMapping("/me")
    public UserProfileResponse updateProfile(@Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest){
        return userProfileService.updateProfile(updateUserProfileRequest);
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return userProfileService.me();
    }

}
