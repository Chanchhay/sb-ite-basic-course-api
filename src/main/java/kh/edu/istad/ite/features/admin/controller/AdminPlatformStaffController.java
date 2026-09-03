package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
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

/**
 * Access to every endpoint here is enforced in SecurityConfig via the
 * role:read/assign permissions (or SUPER_ADMIN outright) — not re-checked
 * here.
 */
@RestController
@RequestMapping("/api/v1/platform/staff")
@RequiredArgsConstructor
public class AdminPlatformStaffController {

    private final StaffManagementService staffService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createStaff(@Valid @RequestBody CreateStaffRequest request) {
        staffService.createPlatformStaff(request);
    }

    @GetMapping
    public List<StaffResponse> getStaffList() {
        return staffService.getPlatformStaffList();
    }

    @GetMapping("/{userId}")
    public StaffResponse getStaffDetail(@PathVariable UUID userId) {
        return staffService.getPlatformStaffDetail(userId);
    }

    @PutMapping("/{userId}")
    public void updateStaff(@PathVariable UUID userId, @Valid @RequestBody UpdateStaffRequest request) {
        staffService.updatePlatformStaff(userId, request);
    }

    @PatchMapping("/{userId}/status")
    public void updateStaffStatus(@PathVariable UUID userId, @Valid @RequestBody StaffStatusRequest request) {
        staffService.changePlatformStaffStatus(userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaff(@PathVariable UUID userId) {
        staffService.deletePlatformStaff(userId);
    }
}
