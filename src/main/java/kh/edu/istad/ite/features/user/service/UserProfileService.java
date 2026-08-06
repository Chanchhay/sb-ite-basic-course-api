package kh.edu.istad.ite.features.user.service;

import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse me();

    UserProfileResponse updateProfile(UpdateUserProfileRequest updateUserProfileRequest);

    UserProfileResponse uploadProfilePicture(MultipartFile file);

    void removeProfilePicture();

    List<UserProfile> findByBusinessIdAndStaffStatus(UUID businessId, RecordStatus status);

}