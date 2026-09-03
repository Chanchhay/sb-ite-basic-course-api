package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.admin.dto.PlatformRolePatchRequest;
import kh.edu.istad.ite.features.admin.dto.PlatformRoleRequest;
import kh.edu.istad.ite.features.admin.dto.PlatformRoleResponse;
import kh.edu.istad.ite.features.business.service.KeycloakRoleAdapter;
import kh.edu.istad.ite.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Access to every endpoint here is enforced in SecurityConfig via the
 * role:read/create/update/delete permissions (or SUPER_ADMIN outright) —
 * not re-checked here.
 */
@RestController
@RequestMapping("/api/v1/platform/roles")
@RequiredArgsConstructor
public class AdminPlatformRoleController {

    private final KeycloakRoleAdapter roleAdapter;

    @GetMapping
    public PageResponse<PlatformRoleResponse> getRoles(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return roleAdapter.getPlatformRoles(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createRole(@Valid @RequestBody PlatformRoleRequest request) {
        roleAdapter.createPlatformRole(request);
    }

    @PutMapping("/{roleId}")
    public void updateRole(@PathVariable String roleId, @Valid @RequestBody PlatformRoleRequest request) {
        roleAdapter.updatePlatformRole(roleId, request);
    }

    @PatchMapping("/{roleId}")
    public void patchRole(@PathVariable String roleId, @Valid @RequestBody PlatformRolePatchRequest request) {
        roleAdapter.patchPlatformRole(roleId, request);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable String roleId) {
        roleAdapter.deletePlatformRole(roleId);
    }
}
