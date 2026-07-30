package kh.edu.istad.ite.features.business.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.features.admin.dto.PlatformRolePatchRequest;
import kh.edu.istad.ite.features.admin.dto.PlatformRoleRequest;
import kh.edu.istad.ite.features.admin.dto.PlatformRoleResponse;
import kh.edu.istad.ite.features.business.dto.BusinessRolePatchRequest;
import kh.edu.istad.ite.features.business.dto.BusinessRoleRequest;
import kh.edu.istad.ite.features.business.dto.BusinessRoleResponse;
import kh.edu.istad.ite.features.business.mapper.RoleMapper;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.enums.PermissionCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakRoleAdapter {

    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
    private final BusinessRepository businessRepository;
    private final RoleMapper roleMapper;

    private String getPrefix(UUID businessId) {
        return "biz_" + businessId + "_";
    }

    private String slugify(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-$|^+", "");
    }

    private String resolveRoleSlug(String roleIdOrSlug) {
        if (roleIdOrSlug.startsWith("biz_") || roleIdOrSlug.startsWith("platform_")) {
            return roleIdOrSlug;
        } else {
            try {
                return keycloak.realm(props.getTargetRealm()).rolesById().getRole(roleIdOrSlug).getName();
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
            }
        }
    }

    // --- Core Role Operations (Unified) ---

    private List<String> internalGetRolePermissions(RoleResource roleResource) {
        Set<RoleRepresentation> composites = roleResource.getRoleComposites();
        if (composites == null) return List.of();
        return composites.stream().map(RoleRepresentation::getName).toList();
    }

    private void internalUpdateRole(String roleSlug, String expectedPrefix, String newName, List<String> permissions, boolean isPlatform) {
        if (!roleSlug.startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify role outside its scope");
        }

        RoleResource roleResource = keycloak.realm(props.getTargetRealm()).roles().get(roleSlug);
        RoleRepresentation roleRep = roleResource.toRepresentation();

        if (newName != null && !newName.equals(roleRep.getDescription())) {
            roleRep.setDescription(newName);
            roleResource.update(roleRep);
        }

        internalUpdateRolePermissions(roleSlug, permissions, isPlatform);
    }

    private void internalUpdateRolePermissions(String roleSlug, List<String> requestedPermissions, boolean isPlatform) {
        if (requestedPermissions == null) return;

        RoleResource targetRole = keycloak.realm(props.getTargetRealm()).roles().get(roleSlug);

        Set<RoleRepresentation> existingComposites = targetRole.getRoleComposites();
        if (existingComposites != null && !existingComposites.isEmpty()) {
            targetRole.deleteComposites(existingComposites.stream().toList());
        }

        if (requestedPermissions.isEmpty()) {
            return;
        }

        Predicate<PermissionCode> assignableCheck = isPlatform 
                ? PermissionCode::isPlatformStaffAssignable 
                : PermissionCode::isBusinessStaffAssignable;
        String scopeErrorMsg = isPlatform ? "platform staff" : "business staff";

        List<RoleRepresentation> newComposites = requestedPermissions.stream()
                .filter(perm -> perm != null && !perm.trim().isEmpty())
                .map(perm -> {
                    PermissionCode code = PermissionCode.fromCode(perm);
                    if (code == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission: " + perm);
                    }
                    if (!assignableCheck.test(code)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission cannot be assigned to " + scopeErrorMsg + ": " + perm);
                    }

                    try {
                        String clientId = keycloak.realm(props.getTargetRealm()).clients().findByClientId("fluxipos-backend").get(0).getId();
                        return keycloak.realm(props.getTargetRealm()).clients().get(clientId).roles().get(perm).toRepresentation();
                    } catch (Exception e) {
                        log.error("Failed to find client role for permission: {}", perm, e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Permission setup error in Keycloak");
                    }
                }).toList();

        if (!newComposites.isEmpty()) {
            targetRole.addComposites(newComposites);
        }
    }

    private void internalDeleteRole(String roleSlug, String expectedPrefix) {
        if (!roleSlug.startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete role outside its scope");
        }
        keycloak.realm(props.getTargetRealm()).roles().deleteRole(roleSlug);
    }

    // --- Business Roles ---

    public List<BusinessRoleResponse> getBusinessRoles(UUID businessId) {
        String prefix = getPrefix(businessId);
        RolesResource rolesResource = keycloak.realm(props.getTargetRealm()).roles();

        return rolesResource.list().stream()
                .filter(r -> r.getName().startsWith(prefix))
                .map(r -> {
                    RoleResource roleResource = rolesResource.get(r.getName());
                    List<String> perms = internalGetRolePermissions(roleResource);
                    return roleMapper.toBusinessRoleResponse(r, perms);
                })
                .toList();
    }

    public void createBusinessRole(UUID businessId, BusinessRoleRequest request) {
        String prefix = getPrefix(businessId);
        String roleSlug = prefix + slugify(request.name());
        RolesResource rolesResource = keycloak.realm(props.getTargetRealm()).roles();

        if (rolesResource.list(roleSlug, true).stream().anyMatch(r -> r.getName().equals(roleSlug))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists");
        }

        RoleRepresentation newRole = new RoleRepresentation();
        newRole.setName(roleSlug);
        newRole.setDescription(request.name());
        rolesResource.create(newRole);

        try {
            internalUpdateRolePermissions(roleSlug, request.permissions(), false);
        } catch (Exception e) {
            rolesResource.deleteRole(roleSlug);
            throw e;
        }
    }

    public void updateBusinessRole(UUID businessId, String roleIdOrSlug, BusinessRoleRequest request) {
        internalUpdateRole(resolveRoleSlug(roleIdOrSlug), getPrefix(businessId), request.name(), request.permissions(), false);
    }

    public void patchBusinessRole(UUID businessId, String roleIdOrSlug, BusinessRolePatchRequest request) {
        internalUpdateRole(resolveRoleSlug(roleIdOrSlug), getPrefix(businessId), request.name(), request.permissions(), false);
    }

    public void deleteBusinessRole(UUID businessId, String roleIdOrSlug) {
        internalDeleteRole(resolveRoleSlug(roleIdOrSlug), getPrefix(businessId));
    }

    // --- Platform Roles ---

    public List<PlatformRoleResponse> getPlatformRoles() {
        String prefix = "platform_";
        RolesResource rolesResource = keycloak.realm(props.getTargetRealm()).roles();

        return rolesResource.list().stream()
                .filter(r -> r.getName().startsWith(prefix))
                .map(r -> {
                    RoleResource roleResource = rolesResource.get(r.getName());
                    List<String> perms = internalGetRolePermissions(roleResource);
                    return roleMapper.toPlatformRoleResponse(r, perms);
                })
                .toList();
    }

    public void createPlatformRole(PlatformRoleRequest request) {
        String prefix = "platform_";
        String roleSlug = prefix + slugify(request.name());
        RolesResource rolesResource = keycloak.realm(props.getTargetRealm()).roles();

        if (rolesResource.list(roleSlug, true).stream().anyMatch(r -> r.getName().equals(roleSlug))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists");
        }

        RoleRepresentation newRole = new RoleRepresentation();
        newRole.setName(roleSlug);
        newRole.setDescription(request.name());
        rolesResource.create(newRole);

        try {
            internalUpdateRolePermissions(roleSlug, request.permissions(), true);
        } catch (Exception e) {
            rolesResource.deleteRole(roleSlug);
            throw e;
        }
    }

    public void updatePlatformRole(String roleIdOrSlug, PlatformRoleRequest request) {
        internalUpdateRole(resolveRoleSlug(roleIdOrSlug), "platform_", request.name(), request.permissions(), true);
    }

    public void patchPlatformRole(String roleIdOrSlug, PlatformRolePatchRequest request) {
        internalUpdateRole(resolveRoleSlug(roleIdOrSlug), "platform_", request.name(), request.permissions(), true);
    }

    public void deletePlatformRole(String roleIdOrSlug) {
        internalDeleteRole(resolveRoleSlug(roleIdOrSlug), "platform_");
    }
}
