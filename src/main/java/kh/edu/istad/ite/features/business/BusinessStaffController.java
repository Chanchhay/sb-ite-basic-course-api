package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.BusinessSecurityValidator;
import kh.edu.istad.ite.features.user.dto.CreateStaffRequest;
import kh.edu.istad.ite.features.user.dto.StaffResponse;
import kh.edu.istad.ite.features.user.dto.StaffStatusRequest;
import kh.edu.istad.ite.features.user.dto.UpdateStaffRequest;
import kh.edu.istad.ite.features.user.service.StaffManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createStaff(@PathVariable UUID businessId, @Valid @RequestBody CreateStaffRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        staffService.createBusinessStaff(businessId, request);
    }

    @GetMapping
    public Page<StaffResponse> getStaffList(
            @PathVariable UUID businessId,
            @PageableDefault(sort = "JoinedAt", direction = Sort.Direction.DESC)Pageable pageable
            ) {
        securityValidator.validateBusinessOwner(businessId);
        return staffService.getBusinessStaffList(businessId , pageable);
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
    }

    @PatchMapping("/{userId}/status")
    public void updateStaffStatus(@PathVariable UUID businessId, @PathVariable UUID userId, @Valid @RequestBody StaffStatusRequest request) {
        securityValidator.validateBusinessOwner(businessId);
        staffService.changeBusinessStaffStatus(businessId, userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaff(@PathVariable UUID businessId, @PathVariable UUID userId) {
        securityValidator.validateBusinessOwner(businessId);
        staffService.deleteBusinessStaff(businessId, userId);
    }
}
