package kh.edu.istad.ite.features.user.service;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.features.auth.dto.RoleEnum;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.dto.CreateStaffRequest;
import kh.edu.istad.ite.features.user.dto.StaffResponse;
import kh.edu.istad.ite.features.user.dto.StaffStatusRequest;
import kh.edu.istad.ite.features.user.dto.UpdateStaffRequest;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.mapper.UserProfileMapper;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffManagementService {
    private static final String PHONE_NUMBER_ATTRIBUTE = "phone_number";
    private static final String GENDER_ATTRIBUTE = "gender";

    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
    private final UserProfileRepository userProfileRepository;
    private final BusinessRepository businessRepository;
    private final UserProfileMapper userProfileMapper;

    @Transactional
    public void createBusinessStaff(UUID businessId, CreateStaffRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));

        if (request.roleId() != null) {
            validateRole(request.roleId(), "biz_" + businessId + "_");
        }

        String userId = provisionKeycloakUser(request);
        try {
            assignRoles(userId, request.roleId());
            saveUserProfile(userId, request, business);
        } catch (Exception e) {
            rollbackKeycloakUser(userId);
            throw e;
        }
    }

    @Transactional
    public void createPlatformStaff(CreateStaffRequest request) {
        if (request.roleId() != null) {
            validateRole(request.roleId(), "platform_");
        }

        String userId = provisionKeycloakUser(request);
        try {
            assignRoles(userId, request.roleId());
            saveUserProfile(userId, request, null);
        } catch (Exception e) {
            rollbackKeycloakUser(userId);
            throw e;
        }
    }

    private void validateRole(String roleId, String expectedPrefix) {
        try {
            String roleName = keycloak.realm(props.getTargetRealm()).rolesById().getRole(roleId).getName();
            if (!roleName.startsWith(expectedPrefix)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign role outside of allowed scope");
            }
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak connection error");
        }
    }

    private String provisionKeycloakUser(CreateStaffRequest request) {
        UsersResource usersResource = keycloak.realm(props.getTargetRealm()).users();
        
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setAttributes(Map.of(
                PHONE_NUMBER_ATTRIBUTE, List.of(request.phoneNumber()),
                GENDER_ATTRIBUTE, List.of(request.gender())
        ));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);

        user.setCredentials(List.of(credential));
        user.setEnabled(true);
        user.setEmailVerified(true);

        try (Response response = usersResource.create(user)) {
            int status = response.getStatus();
            if (status == HttpStatus.CREATED.value()) {
                return CreatedResponseUtil.getCreatedId(response);
            }
            if (status == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
            }
            if (status == HttpStatus.BAD_REQUEST.value()) {
                String error = response.readEntity(String.class);
                log.error("Keycloak 400 error: {}", error);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user data: " + error);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak failed to create user, status: " + status);
        } catch (ProcessingException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to connect to Keycloak", e);
        }
    }

    private void assignRoles(String userId, String customRoleId) {
        UserResource userResource = keycloak.realm(props.getTargetRealm()).users().get(userId);
        RolesResource rolesResource = keycloak.realm(props.getTargetRealm()).roles();

        Set<RoleRepresentation> rolesToAdd = new LinkedHashSet<>();
        rolesToAdd.add(rolesResource.get(RoleEnum.USER.name()).toRepresentation());
        
        if (customRoleId != null) {
            rolesToAdd.add(keycloak.realm(props.getTargetRealm()).rolesById().getRole(customRoleId));
        }

        userResource.roles().realmLevel().add(rolesToAdd.stream().toList());
    }

    private void saveUserProfile(String userId, CreateStaffRequest request, Business business) {
        try {
            UserProfile profile = new UserProfile();
            profile.setUserId(UUID.fromString(userId));
            profile.setPhoneNumber(request.phoneNumber());
            profile.setGender(request.gender());
            profile.setStaffStatus(RecordStatus.ACTIVE);
            if (business != null) {
                profile.setBusiness(business);
            }
            userProfileRepository.save(profile);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save user profile to database", e);
        }
    }

    private void rollbackKeycloakUser(String userId) {
        try {
            keycloak.realm(props.getTargetRealm()).users().delete(userId);
            log.info("Rolled back Keycloak user {}", userId);
        } catch (Exception e) {
            log.error("Failed to rollback Keycloak user {}", userId, e);
        }
    }

    public PageResponse<StaffResponse> getBusinessStaffList(UUID businessId, Pageable pageable) {
        Page<UserProfile> profiles = userProfileRepository.findByBusinessId(businessId, pageable);
        List<StaffResponse> content = profiles.stream()
                .map(this::mapToStaffResponseOrNull)
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.from(new PageImpl<>(content, pageable, profiles.getTotalElements()));
    }

    public List<StaffResponse> getPlatformStaffList() {
        return userProfileRepository.findByBusinessIdOrderByJoinedAtDesc(null).stream()
                .map(this::mapToStaffResponseOrNull)
                .filter(Objects::nonNull)
                .toList();
    }

    public StaffResponse getBusinessStaffDetail(UUID businessId, UUID userId) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        return mapToStaffResponseOrThrow404(profile);
    }

    public StaffResponse getPlatformStaffDetail(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        return mapToStaffResponseOrThrow404(profile);
    }

    /** List views: an orphaned profile shouldn't fail the whole page, so it's dropped (and logged). */
    private StaffResponse mapToStaffResponseOrNull(UserProfile profile) {
        try {
            return mapToStaffResponse(profile);
        } catch (OrphanedStaffProfileException e) {
            log.warn(
                    "UserProfile {} has no matching Keycloak user (deleted directly in Keycloak?) — " +
                            "omitting from the staff list. Delete this orphaned profile to clear this warning.",
                    profile.getUserId());
            return null;
        }
    }

    /** Detail views: the caller asked for exactly this one, so an orphaned profile is a 404, not a 500. */
    private StaffResponse mapToStaffResponseOrThrow404(UserProfile profile) {
        try {
            return mapToStaffResponse(profile);
        } catch (OrphanedStaffProfileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account no longer exists in the identity provider");
        }
    }

    private static final class OrphanedStaffProfileException extends RuntimeException {
    }

    private StaffResponse mapToStaffResponse(UserProfile profile) {
        UserResource userResource = keycloak.realm(props.getTargetRealm()).users().get(profile.getUserId().toString());
        UserRepresentation keycloakUser;
        List<RoleRepresentation> roles;

        try {
            keycloakUser = userResource.toRepresentation();
            roles = userResource.roles().realmLevel().listAll();
        } catch (NotFoundException e) {
            throw new OrphanedStaffProfileException();
        } catch (Exception e) {
            log.error("Failed to fetch Keycloak user for ID {}", profile.getUserId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving staff details");
        }

        String roleId = roles.stream()
                .filter(r -> r.getName().startsWith("biz_") || r.getName().startsWith("platform_"))
                .map(RoleRepresentation::getId)
                .findFirst()
                .orElse(null);

        return userProfileMapper.toStaffResponse(keycloakUser, profile, roleId);
    }

    @Transactional
    public void updateBusinessStaff(UUID businessId, UUID userId, UpdateStaffRequest request) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        if (request.roleId() != null) {
            validateRole(request.roleId(), "biz_" + businessId + "_");
        }
        performUpdate(profile, request);
    }

    @Transactional
    public void updatePlatformStaff(UUID userId, UpdateStaffRequest request) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        if (request.roleId() != null) {
            validateRole(request.roleId(), "platform_");
        }
        performUpdate(profile, request);
    }

    private void performUpdate(UserProfile profile, UpdateStaffRequest request) {
        profile.setPhoneNumber(request.phoneNumber());
        profile.setGender(request.gender());
        userProfileRepository.save(profile);

        UserResource userResource = keycloak.realm(props.getTargetRealm()).users().get(profile.getUserId().toString());
        UserRepresentation keycloakUser = userResource.toRepresentation();
        keycloakUser.setFirstName(request.firstName());
        keycloakUser.setLastName(request.lastName());
        userResource.update(keycloakUser);

        List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();
        List<RoleRepresentation> rolesToRemove = currentRoles.stream()
                .filter(r -> r.getName().startsWith("biz_") || r.getName().startsWith("platform_"))
                .toList();
        
        if (!rolesToRemove.isEmpty()) {
            userResource.roles().realmLevel().remove(rolesToRemove);
        }

        if (request.roleId() != null) {
            RoleRepresentation roleToAdd = keycloak.realm(props.getTargetRealm()).rolesById().getRole(request.roleId());
            userResource.roles().realmLevel().add(List.of(roleToAdd));
        }
    }

    @Transactional
    public void changeBusinessStaffStatus(UUID businessId, UUID userId, StaffStatusRequest request) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        performChangeStatus(profile, request.status());
    }

    @Transactional
    public void changePlatformStaffStatus(UUID userId, StaffStatusRequest request) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        performChangeStatus(profile, request.status());
    }

    private void performChangeStatus(UserProfile profile, RecordStatus newStatus) {
        profile.setStaffStatus(newStatus);
        userProfileRepository.save(profile);

        UserResource userResource = keycloak.realm(props.getTargetRealm()).users().get(profile.getUserId().toString());
        UserRepresentation keycloakUser = userResource.toRepresentation();
        keycloakUser.setEnabled(newStatus == RecordStatus.ACTIVE);
        userResource.update(keycloakUser);
    }

    @Transactional
    public void deleteBusinessStaff(UUID businessId, UUID userId) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        performDelete(profile);
    }

    @Transactional
    public void deletePlatformStaff(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserIdAndBusinessId(userId, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
        performDelete(profile);
    }

    private void performDelete(UserProfile profile) {
        userProfileRepository.delete(profile);
        keycloak.realm(props.getTargetRealm()).users().delete(profile.getUserId().toString());
    }
}
