package kh.edu.istad.ite.features.auth;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.features.auth.dto.RegisterRequest;
import kh.edu.istad.ite.features.auth.dto.RegisterResponse;
import kh.edu.istad.ite.features.auth.dto.RoleEnum;
import kh.edu.istad.ite.features.auth.mapper.AuthMapper;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
    private static final String PHONE_NUMBER_ATTRIBUTE = "phone_number";
    private static final String GENDER_ATTRIBUTE = "gender";

    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
    private final AuthMapper authMapper;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (!registerRequest.password().equals(registerRequest.confirmPassword())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match");
        }

        UsersResource usersResource = keycloak.realm(props.getTargetRealm()).users();
        UserRepresentation userRepresentation = buildUserRepresentation(registerRequest);
        String createdUserId = createKeycloakUser(usersResource, userRepresentation);

        try {
            UserResource userResource = usersResource.get(createdUserId);
            assignRoles(userResource, registerRequest.role());
            saveUserProfile(createdUserId, registerRequest);
            sendVerificationEmail(userResource, createdUserId);

            UserRepresentation createdUser = userResource.toRepresentation();
            return authMapper.toRegisterResponse(createdUser, registerRequest.role());
        } catch (NotFoundException e) {
            cleanupCreatedUser(usersResource, createdUserId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Required Keycloak user or role was not found", e);
        } catch (WebApplicationException | ProcessingException e) {
            cleanupCreatedUser(usersResource, createdUserId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak failed while completing user registration", e);
        } catch (DataAccessException e) {
            cleanupCreatedUser(usersResource, createdUserId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save user profile", e);
        } catch (RuntimeException e) {
            cleanupCreatedUser(usersResource, createdUserId);
            throw e;
        }
    }

    private UserRepresentation buildUserRepresentation(RegisterRequest registerRequest) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(registerRequest.username());
        userRepresentation.setEmail(registerRequest.email());
        userRepresentation.setFirstName(registerRequest.firstName());
        userRepresentation.setLastName(registerRequest.lastName());
        userRepresentation.setAttributes(Map.of(
                PHONE_NUMBER_ATTRIBUTE, List.of(registerRequest.phoneNumber()),
                GENDER_ATTRIBUTE, List.of(registerRequest.gender())
        ));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(registerRequest.password());
        credential.setTemporary(false);

        userRepresentation.setCredentials(List.of(credential));
        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(false);

        return userRepresentation;
    }

    private String createKeycloakUser(UsersResource usersResource, UserRepresentation userRepresentation) {
        try (Response response = usersResource.create(userRepresentation)) {
            int status = response.getStatus();
            log.info("Keycloak create user response status: {}", status);

            if (status == HttpStatus.CREATED.value()) {
                String createdUserId = CreatedResponseUtil.getCreatedId(response);
                log.info("Created Keycloak user: {}", createdUserId);
                return createdUserId;
            }

            if (status == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
            }

            if (status == HttpStatus.BAD_REQUEST.value()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid registration data");
            }

            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Keycloak admin client is not authorized to create users");
            }

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak failed to create user");
        } catch (ProcessingException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to connect to Keycloak", e);
        } catch (WebApplicationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak rejected user creation", e);
        }
    }

    private void assignRoles(UserResource userResource, String selectedRole) {
        RolesResource roleResource = keycloak.realm(props.getTargetRealm()).roles();
        Set<String> roleNames = new LinkedHashSet<>();
        roleNames.add(RoleEnum.USER.name());
        roleNames.add(RoleEnum.valueOf(selectedRole).name());

        List<RoleRepresentation> roles = roleNames.stream()
                .map(roleName -> roleResource.get(roleName).toRepresentation())
                .toList();
        userResource.roles().realmLevel().add(roles);
    }

    private void saveUserProfile(String createdUserId, RegisterRequest registerRequest) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(UUID.fromString(createdUserId));
        userProfile.setPhoneNumber(registerRequest.phoneNumber());
        userProfile.setGender(registerRequest.gender());
        userProfileRepository.save(userProfile);
    }

    private void sendVerificationEmail(UserResource userResource, String createdUserId) {
        if (!props.isSendVerificationEmail()) {
            log.info("Skipping verification email for Keycloak user {}", createdUserId);
            return;
        }

        try {
            userResource.sendVerifyEmail();
        } catch (WebApplicationException | ProcessingException e) {
            log.warn("Failed to send verification email for Keycloak user {}: {}", createdUserId, e.getMessage());
        }
    }

    private void cleanupCreatedUser(UsersResource usersResource, String createdUserId) {
        try {
            usersResource.delete(createdUserId);
            log.info("Deleted Keycloak user {} after registration failure", createdUserId);
        } catch (WebApplicationException | ProcessingException e) {
            log.error("Failed to delete Keycloak user {} after registration failure", createdUserId, e);
        }
    }
}
