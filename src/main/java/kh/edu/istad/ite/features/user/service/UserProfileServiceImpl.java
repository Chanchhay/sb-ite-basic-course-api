package kh.edu.istad.ite.features.user.service;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
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
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.notification.dto.CreateNotificationRequest;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import kh.edu.istad.ite.features.notification.service.NotificationCommandService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {
    private static final List<String> APP_ROLE_PRIORITY = List.of(
            "SUPER_ADMIN",
            "BUSINESS",
            "CUSTOMER",
            "GLOBAL_CUSTOMER",
            "USER"
    );

    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
    private final UserProfileMapper userProfileMapper;
    private final UserProfileRepository userProfileRepository;
    private final MinioService minioService;
    private final NotificationCommandService notificationCommandService;


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

        UserProfile userProfile = getOrCreateUserProfile(userUuid);
        userProfileMapper.mapUpdateUserProfileRequestToUserProfile(
                updateUserProfileRequest,
                userProfile
        );

        String newProfilePicture = updateUserProfileRequest.profilePicture();
        if (newProfilePicture != null && !newProfilePicture.isBlank()) {
            String oldKey = userProfile.getProfilePicture();
            userProfile.setProfilePicture(newProfilePicture);

            if (!newProfilePicture.equals(oldKey) && isManagedAsset(oldKey)) {
                minioService.deleteAsset(oldKey);
            }
        }

        userProfileRepository.save(userProfile);

        return userProfileMapper.toUserProfileResponse(
                userRepresentation,
                userProfile,
                resolveRole(userResource)
        );
    }

    @Override
    @Transactional
    public UserProfileResponse uploadProfilePicture(MultipartFile file) {
        validateImage(file);

        String userId = SecurityUtils.extractUserId();
        UUID userUuid = UUID.fromString(userId);

        UserResource userResource = keycloak
                .realm(props.getTargetRealm())
                .users()
                .get(userId);

        UserProfile userProfile = getOrCreateUserProfile(userUuid);
        String oldKey = userProfile.getProfilePicture();
        userProfile.setProfilePicture(minioService.uploadAsset(file));
        userProfileRepository.save(userProfile);

        if (isManagedAsset(oldKey)) {
            minioService.deleteAsset(oldKey);
        }

        return userProfileMapper.toUserProfileResponse(
                userResource.toRepresentation(),
                userProfile,
                resolveRole(userResource)
        );
    }

    @Override
    @Transactional
    public void removeProfilePicture() {
        String userId = SecurityUtils.extractUserId();
        UUID userUuid = UUID.fromString(userId);

        UserProfile userProfile = getOrCreateUserProfile(userUuid);

        String oldKey = userProfile.getProfilePicture();
        if (isManagedAsset(oldKey)) {
            minioService.deleteAsset(oldKey);
        }

        userProfile.setProfilePicture(null);
        userProfileRepository.save(userProfile);
    }

    @Override
    public List<UserProfile> findByBusinessIdAndStaffStatus(UUID businessId, RecordStatus status) {
        return List.of();
    }

    @Override
    @Transactional
    public void notifyStaffLogin() {
        String userId = SecurityUtils.extractUserId();
        if (userId == null || userId.isBlank()) {
            return;
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return;
        }

        UserProfile userProfile = userProfileRepository.findById(userUuid).orElse(null);
        if (userProfile == null || userProfile.getBusiness() == null) {
            return;
        }

        Business business = userProfile.getBusiness();
        UUID boUserId = business.getKeycloakUserId();
        if (boUserId == null || boUserId.equals(userUuid)) {
            // The logged in user is the business owner themself, no alert needed
            return;
        }

        String staffName = "Staff member";
        String staffEmail = "";
        try {
            UserResource userResource = keycloak.realm(props.getTargetRealm()).users().get(userId);
            UserRepresentation keycloakUser = userResource.toRepresentation();
            if (keycloakUser != null) {
                String first = keycloakUser.getFirstName();
                String last = keycloakUser.getLastName();
                if ((first != null && !first.isBlank()) || (last != null && !last.isBlank())) {
                    staffName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                } else if (keycloakUser.getUsername() != null && !keycloakUser.getUsername().isBlank()) {
                    staffName = keycloakUser.getUsername();
                }
                if (keycloakUser.getEmail() != null && !keycloakUser.getEmail().isBlank()) {
                    staffEmail = keycloakUser.getEmail();
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve Keycloak user info for staff login notification: {}", e.getMessage());
        }

        String businessName = business.getDisplayName() != null && !business.getDisplayName().isBlank()
                ? business.getDisplayName()
                : (business.getBusinessName() != null && !business.getBusinessName().isBlank() ? business.getBusinessName() : "your business");

        String content = String.format("Staff member \"%s\"%s has signed in to %s.",
                staffName,
                staffEmail.isBlank() ? "" : " (" + staffEmail + ")",
                businessName);

        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                userId,
                staffName,
                List.of(boUserId.toString()),
                NotificationType.SYSTEM,
                "Staff Member Signed In",
                content,
                "/employees"
        );

        try {
            notificationCommandService.send(boUserId, notificationRequest);
        } catch (Exception e) {
            log.error("Failed to send staff login notification to BO {}: {}", boUserId, e.getMessage(), e);
        }
    }


    @Override
    public UserProfileResponse me() {
        String userId = SecurityUtils.extractUserId();
        UUID userUuid = UUID.fromString(userId);
        UserResource userResource = keycloak.realm(props.getTargetRealm())
                .users()
                .get(userId);
        UserRepresentation keycloakUser = userResource.toRepresentation();
        UserProfile userProfile = getOrCreateUserProfile(userUuid);

        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile, resolveRole(userResource));
    }

    private UserProfile getOrCreateUserProfile(UUID userUuid) {
        return userProfileRepository.findById(userUuid)
                .orElseGet(() -> {
                    UserProfile newProfile = new UserProfile();
                    newProfile.setUserId(userUuid);
                    return userProfileRepository.save(newProfile);
                });
    }

    private String resolveRole(UserResource userResource) {
        List<RoleRepresentation> effectiveRoles = userResource.roles().realmLevel().listEffective();
        List<String> roleNames = effectiveRoles.stream().map(RoleRepresentation::getName).toList();

        String priorityMatch = APP_ROLE_PRIORITY.stream()
                .filter(name -> !name.equals("USER"))
                .filter(roleNames::contains)
                .findFirst()
                .orElse(null);

        if (priorityMatch != null) {
            return priorityMatch;
        }

        // Platform/business staff carry only the generic "USER" base role plus
        // a named custom role (platform_xxx / biz_<id>_xxx, added after this
        // priority list was written) — show that role's readable name instead
        // of the meaningless "USER" every account has.
        Optional<RoleRepresentation> staffRole = effectiveRoles.stream()
                .filter(role -> role.getName().startsWith("platform_") || role.getName().startsWith("biz_"))
                .findFirst();

        if (staffRole.isPresent()) {
            RoleRepresentation role = staffRole.get();
            return role.getDescription() != null && !role.getDescription().isBlank()
                    ? role.getDescription()
                    : role.getName();
        }

        return roleNames.contains("USER") ? "Staff" : null;
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

    /**
     * A stored picture is only deletable from MinIO when it is an object key we own,
     * not an absolute URL pointing at an external provider (e.g. Keycloak/social login).
     */
    private boolean isManagedAsset(String profilePicture) {
        return profilePicture != null
                && !profilePicture.isBlank()
                && !profilePicture.startsWith("http://")
                && !profilePicture.startsWith("https://");
    }
}
