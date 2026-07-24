package kh.edu.istad.ite.features.user.service;

import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;

public interface UserProfileService {

    UserProfileResponse me();

    UserProfileResponse updateProfile(UpdateUserProfileRequest updateUserProfileRequest);

}