package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.admin.dto.request.PlatformFeatureToggleRequest;
import kh.edu.istad.ite.features.admin.dto.response.PlatformFeatureResponse;
import kh.edu.istad.ite.features.admin.service.AdminPlatformFeatureService;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/platform-features")
@RequiredArgsConstructor
public class AdminPlatformFeatureController {

    private final AdminPlatformFeatureService adminPlatformFeatureService;

    @GetMapping
    public List<PlatformFeatureResponse> findFeatures() {
        return adminPlatformFeatureService.findFeatures();
    }

    // Returns the full set afterwards, so the screen refreshes from one call.
    @PatchMapping("/{feature}")
    public List<PlatformFeatureResponse> toggleFeature(
            @PathVariable BusinessFeature feature,
            @Valid @RequestBody PlatformFeatureToggleRequest request
    ) {
        return adminPlatformFeatureService.toggleFeature(feature, request);
    }
}
