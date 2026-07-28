package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.BusinessSecurityValidator;
import kh.edu.istad.ite.features.business.dto.BusinessRolePatchRequest;
import kh.edu.istad.ite.features.business.dto.BusinessRoleRequest;
import kh.edu.istad.ite.features.business.dto.BusinessRoleResponse;
import kh.edu.istad.ite.features.business.service.KeycloakRoleAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/roles")
@RequiredArgsConstructor
public class BusinessRoleController {

    private final BusinessSecurityValidator securityValidator;
    private final KeycloakRoleAdapter roleAdapter;

    @GetMapping
    public List<BusinessRoleResponse> getRoles(@PathVariable UUID businessId) {
        securityValidator.validateBusinessOwnerOrAdmin(businessId);
        return roleAdapter.getBusinessRoles(businessId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createRole(@PathVariable UUID businessId, @Valid @RequestBody BusinessRoleRequest request) {
        securityValidator.validateBusinessOwnerOrAdmin(businessId);
        roleAdapter.createBusinessRole(businessId, request);
    }

    @PutMapping("/{roleId}")
    public void updateRole(@PathVariable UUID businessId, @PathVariable String roleId, @Valid @RequestBody BusinessRoleRequest request) {
        securityValidator.validateBusinessOwnerOrAdmin(businessId);
        roleAdapter.updateBusinessRole(businessId, roleId, request);
    }

    @PatchMapping("/{roleId}")
    public void patchRole(@PathVariable UUID businessId, @PathVariable String roleId, @Valid @RequestBody BusinessRolePatchRequest request) {
        securityValidator.validateBusinessOwnerOrAdmin(businessId);
        roleAdapter.patchBusinessRole(businessId, roleId, request);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID businessId, @PathVariable String roleId) {
        securityValidator.validateBusinessOwnerOrAdmin(businessId);
        roleAdapter.deleteBusinessRole(businessId, roleId);
    }
}
