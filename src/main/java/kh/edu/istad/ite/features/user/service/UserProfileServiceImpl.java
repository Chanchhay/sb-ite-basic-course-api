package kh.edu.istad.ite.features.user.service;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.config.security.CurrentAuthorizationContext;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.mapper.UserProfileMapper;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import kh.edu.istad.ite.features.minio.MinioService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {
    private final CurrentAuthorizationContext currentAuthorizationContext;
    
    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
    private final UserProfileMapper userProfileMapper;
    private final UserProfileRepository userProfileRepository;
    private final MinioService minioService;


    @Override
    @Transactional
    public UserProfileResponse updateProfile(UpdateUserProfileRequest updateUserProfileRequest) {
        String userId = SecurityUtils.extractUserId();
        UUID userUuid = UUID.fromString(userId);

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

        UserProfile userProfile = userProfileRepository.findById(userUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));
        userProfileMapper.mapUpdateUserProfileRequestToUserProfile(
                updateUserProfileRequest,
                userProfile
        );

        if (updateUserProfileRequest.file() != null && !updateUserProfileRequest.file().isEmpty()) {
            validateImage(updateUserProfileRequest.file());
            String oldKey = userProfile.getProfilePicture();
            userProfile.setProfilePicture(minioService.uploadAsset(updateUserProfileRequest.file()));

            if (oldKey != null && !oldKey.isBlank() && !oldKey.startsWith("http://") && !oldKey.startsWith("https://")) {
                minioService.deleteAsset(oldKey);
            }
        }

        userProfileRepository.save(userProfile);

        return userProfileMapper.toUserProfileResponse(
                userRepresentation,
                userProfile,
                currentAuthorizationContext.getCurrentRole()
        );
    }

    @Override
    @Transactional
    public void removeProfilePicture() {
        String userId = SecurityUtils.extractUserId();
        UUID userUuid = UUID.fromString(userId);

        UserProfile userProfile = userProfileRepository.findById(userUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));

        String oldKey = userProfile.getProfilePicture();
        if (oldKey != null && !oldKey.isBlank() && !oldKey.startsWith("http://") && !oldKey.startsWith("https://")) {
            minioService.deleteAsset(oldKey);
        }

        userProfile.setProfilePicture(null);
        userProfileRepository.save(userProfile);
    }

    @Override
    public List<UserProfile> findByBusinessIdAndUserStatus(UUID businessId, RecordStatus status) {
        return List.of();
    }


    @Override
    public UserProfileResponse me() {
        String userId = SecurityUtils.extractUserId();
        UUID userUuid = UUID.fromString(userId);
        UserResource userResource = keycloak.realm(props.getTargetRealm())
                .users()
                .get(userId);
        UserRepresentation keycloakUser = userResource.toRepresentation();
        UserProfile userProfile = userProfileRepository.findById(userUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));

        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile, currentAuthorizationContext.getCurrentRole());
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file cannot be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }
    }
}
