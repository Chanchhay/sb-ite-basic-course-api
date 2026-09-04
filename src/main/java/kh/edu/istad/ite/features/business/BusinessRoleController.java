package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.BusinessSecurityValidator;
import kh.edu.istad.ite.features.audit.service.BusinessAuditService;
import kh.edu.istad.ite.features.business.dto.BusinessRolePatchRequest;
import kh.edu.istad.ite.features.business.dto.BusinessRoleRequest;
import kh.edu.istad.ite.features.business.dto.BusinessRoleResponse;
import kh.edu.istad.ite.features.business.service.KeycloakRoleAdapter;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final BusinessAuditService auditService;

    @GetMapping
    public PageResponse<BusinessRoleResponse> getRoles(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        securityValidator.validateBusinessOwner(businessId);
        return roleAdapter.getBusinessRoles(businessId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createRole(@PathVariable UUID businessId, @Valid @RequestBody BusinessRoleRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        roleAdapter.createBusinessRole(businessId, request);

        auditService.record(businessId, BusinessAuditAction.ROLE_CREATED,
                BusinessAuditTarget.ROLE, null, request.name(),
                null, permissionSummary(request.permissions()));
    }

    @PutMapping("/{roleId}")
    public void updateRole(@PathVariable UUID businessId, @PathVariable String roleId, @Valid @RequestBody BusinessRoleRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        roleAdapter.updateBusinessRole(businessId, roleId, request);

        auditService.record(businessId, BusinessAuditAction.ROLE_UPDATED,
                BusinessAuditTarget.ROLE, roleId, request.name(),
                null, permissionSummary(request.permissions()));
    }

    @PatchMapping("/{roleId}")
    public void patchRole(@PathVariable UUID businessId, @PathVariable String roleId, @Valid @RequestBody BusinessRolePatchRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        roleAdapter.patchBusinessRole(businessId, roleId, request);

        auditService.record(businessId, BusinessAuditAction.ROLE_UPDATED,
                BusinessAuditTarget.ROLE, roleId, request.name(),
                null, permissionSummary(request.permissions()));
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID businessId, @PathVariable String roleId) {
        securityValidator.validateBusinessOwner(businessId);
        roleAdapter.deleteBusinessRole(businessId, roleId);

        auditService.record(businessId, BusinessAuditAction.ROLE_DELETED,
                BusinessAuditTarget.ROLE, roleId, null, null, null);
    }

    /**
     * What a role was left holding, short enough to read in a table cell.
     *
     * The full list can run to dozens of codes; the count plus the first few
     * says whether a role was widened or narrowed, which is the question an
     * audit row is being scanned for.
     */
    private static String permissionSummary(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "no permissions";
        }

        String head = String.join(", ", permissions.subList(0, Math.min(3, permissions.size())));

        return permissions.size() <= 3
                ? head
                : head + " and " + (permissions.size() - 3) + " more";
    }
}
