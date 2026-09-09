package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.BusinessSecurityValidator;
import kh.edu.istad.ite.features.audit.service.BusinessAuditService;
import kh.edu.istad.ite.features.user.dto.CreateStaffRequest;
import kh.edu.istad.ite.features.user.dto.StaffResponse;
import kh.edu.istad.ite.features.user.dto.StaffStatusRequest;
import kh.edu.istad.ite.features.user.dto.UpdateStaffRequest;
import kh.edu.istad.ite.features.user.service.StaffManagementService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/staff")
@RequiredArgsConstructor
public class BusinessStaffController {

    private final BusinessSecurityValidator securityValidator;
    private final StaffManagementService staffService;
    private final BusinessAuditService auditService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createStaff(@PathVariable UUID businessId, @Valid @RequestBody CreateStaffRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        staffService.createBusinessStaff(businessId, request);

        // Recorded here rather than in the service: the audit trail is about
        // what was done through the back office, and the controller is the
        // layer that knows a request came from there at all. Nothing below is
        // reached unless the change itself succeeded.
        auditService.record(businessId, BusinessAuditAction.STAFF_CREATED,
                BusinessAuditTarget.STAFF, null, staffLabel(request.firstName(),
                        request.lastName(), request.username()), null, null);
    }

    @GetMapping
    public PageResponse<StaffResponse> getStaffList(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        securityValidator.validateBusinessOwner(businessId);
        return staffService.getBusinessStaffList(businessId, pageable);
    }

    @GetMapping("/{userId}")
    public StaffResponse getStaffDetail(@PathVariable UUID businessId, @PathVariable UUID userId) {
        securityValidator.validateBusinessOwner(businessId);
        return staffService.getBusinessStaffDetail(businessId, userId);
    }

    @PutMapping("/{userId}")
    public void updateStaff(@PathVariable UUID businessId, @PathVariable UUID userId, @Valid @RequestBody UpdateStaffRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        staffService.updateBusinessStaff(businessId, userId, request);

        auditService.record(businessId, BusinessAuditAction.STAFF_UPDATED,
                BusinessAuditTarget.STAFF, userId.toString(),
                staffLabel(request.firstName(), request.lastName(), null), null, null);
    }

    @PatchMapping("/{userId}/status")
    public void updateStaffStatus(@PathVariable UUID businessId, @PathVariable UUID userId, @Valid @RequestBody StaffStatusRequest request) {
        securityValidator.validateBusinessOwner(businessId);

        // Read before the change, so the entry can say what it was before.
        StaffResponse before = staffService.getBusinessStaffDetail(businessId, userId);

        staffService.changeBusinessStaffStatus(businessId, userId, request);

        BusinessAuditAction action = RecordStatus.ACTIVE.equals(request.status())
                ? BusinessAuditAction.STAFF_REACTIVATED
                : BusinessAuditAction.STAFF_SUSPENDED;

        auditService.record(businessId, action, BusinessAuditTarget.STAFF,
                userId.toString(),
                staffLabel(before.firstName(), before.lastName(), before.username()),
                before.status() == null ? null : before.status().name(),
                request.status().name());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaff(@PathVariable UUID businessId, @PathVariable UUID userId) {
        securityValidator.validateBusinessOwner(businessId);

        // Their name is about to stop existing; the log has to hold it, or the
        // entry reads as an id nobody can look up.
        StaffResponse before = staffService.getBusinessStaffDetail(businessId, userId);

        staffService.deleteBusinessStaff(businessId, userId);

        auditService.record(businessId, BusinessAuditAction.STAFF_DELETED,
                BusinessAuditTarget.STAFF, userId.toString(),
                staffLabel(before.firstName(), before.lastName(), before.username()),
                null, null);
    }

    /** A staff member as a person, falling back to whatever we have. */
    private static String staffLabel(String firstName, String lastName, String username) {
        String name = ((firstName == null ? "" : firstName)
                + " " + (lastName == null ? "" : lastName)).trim();

        return name.isEmpty() ? username : name;
    }
}
