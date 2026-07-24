package kh.edu.istad.ite.features.user.service;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.mapper.UserProfileMapper;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
    private final UserProfileMapper userProfileMapper;
    private final UserProfileRepository userProfileRepository;


    @Override
    public UserProfileResponse updateProfile(UpdateUserProfileRequest updateUserProfileRequest) {
        // Get current logged in userId
        String userId = SecurityUtils.extractUserId();

        // Update profile in Keycloak
        UserResource userResource = keycloak
                .realm(props.getTargetRealm())
                .users()
                .get(userId);
        UserRepresentation userRepresentation = userResource.toRepresentation();
        userProfileMapper.mapUpdateUserProfileRequestToUserRepresentation(
                updateUserProfileRequest,
                userRepresentation
        );
        userResource.update(userRepresentation);

        // Update profile in Database
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));
        userProfileMapper.mapUpdateUserProfileRequestToUserProfile(
                updateUserProfileRequest,
                userProfile
        );
        userProfileRepository.save(userProfile);

        return userProfileMapper.toUserProfileResponse(
                userRepresentation,
                userProfile
        );
    }


    @Override
    public UserProfileResponse me() {
        // 1. Profile from Keycloak by userId
        String userId = SecurityUtils.extractUserId();
        UserRepresentation keycloakUser = keycloak.realm(props.getTargetRealm())
                .users()
                .get(userId)
                .toRepresentation();

        // 2. Profile from Database by userId
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));

        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile);
    }

}