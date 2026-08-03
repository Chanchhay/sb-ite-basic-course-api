package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.AdminSecurityValidator;
import kh.edu.istad.ite.features.user.dto.CreateStaffRequest;
import kh.edu.istad.ite.features.user.dto.StaffResponse;
import kh.edu.istad.ite.features.user.dto.StaffStatusRequest;
import kh.edu.istad.ite.features.user.dto.UpdateStaffRequest;
import kh.edu.istad.ite.features.user.service.StaffManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/staff")
@RequiredArgsConstructor
public class AdminPlatformStaffController {

    private final AdminSecurityValidator securityValidator;
    private final StaffManagementService staffService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createStaff(@Valid @RequestBody CreateStaffRequest request) {
        securityValidator.validateSuperAdmin();
        staffService.createPlatformStaff(request);
    }

    @GetMapping
    public List<StaffResponse> getStaffList() {
        securityValidator.validateSuperAdmin();
        return staffService.getPlatformStaffList();
    }

    @GetMapping("/{userId}")
    public StaffResponse getStaffDetail(@PathVariable UUID userId) {
        securityValidator.validateSuperAdmin();
        return staffService.getPlatformStaffDetail(userId);
    }

    @PutMapping("/{userId}")
    public void updateStaff(@PathVariable UUID userId, @Valid @RequestBody UpdateStaffRequest request) {
        securityValidator.validateSuperAdmin();
        staffService.updatePlatformStaff(userId, request);
    }

    @PatchMapping("/{userId}/status")
    public void updateStaffStatus(@PathVariable UUID userId, @Valid @RequestBody StaffStatusRequest request) {
        securityValidator.validateSuperAdmin();
        staffService.changePlatformStaffStatus(userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaff(@PathVariable UUID userId) {
        securityValidator.validateSuperAdmin();
        staffService.deletePlatformStaff(userId);
    }
}
