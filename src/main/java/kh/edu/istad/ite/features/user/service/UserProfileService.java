package kh.edu.istad.ite.features.user.service;

import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.shared.enums.RecordStatus;

import java.util.List;
import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse me();

    UserProfileResponse updateProfile(UpdateUserProfileRequest updateUserProfileRequest);
    List<UserProfile> findByBusinessIdAndUserStatus(UUID businessId, RecordStatus status);

}