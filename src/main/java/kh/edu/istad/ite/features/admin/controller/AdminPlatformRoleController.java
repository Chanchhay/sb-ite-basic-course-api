package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.AdminSecurityValidator;
import kh.edu.istad.ite.features.admin.dto.PlatformRolePatchRequest;
import kh.edu.istad.ite.features.admin.dto.PlatformRoleRequest;
import kh.edu.istad.ite.features.admin.dto.PlatformRoleResponse;
import kh.edu.istad.ite.features.business.service.KeycloakRoleAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/roles")
@RequiredArgsConstructor
public class AdminPlatformRoleController {

    private final AdminSecurityValidator securityValidator;
    private final KeycloakRoleAdapter roleAdapter;

    @GetMapping
    public List<PlatformRoleResponse> getRoles() {
        securityValidator.validateSuperAdmin();
        return roleAdapter.getPlatformRoles();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createRole(@Valid @RequestBody PlatformRoleRequest request) {
        securityValidator.validateSuperAdmin();
        roleAdapter.createPlatformRole(request);
    }

    @PutMapping("/{roleId}")
    public void updateRole(@PathVariable String roleId, @Valid @RequestBody PlatformRoleRequest request) {
        securityValidator.validateSuperAdmin();
        roleAdapter.updatePlatformRole(roleId, request);
    }

    @PatchMapping("/{roleId}")
    public void patchRole(@PathVariable String roleId, @Valid @RequestBody PlatformRolePatchRequest request) {
        securityValidator.validateSuperAdmin();
        roleAdapter.patchPlatformRole(roleId, request);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable String roleId) {
        securityValidator.validateSuperAdmin();
        roleAdapter.deletePlatformRole(roleId);
    }
}
