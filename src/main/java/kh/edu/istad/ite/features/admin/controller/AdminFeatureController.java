package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.admin.dto.request.FeatureToggleRequest;
import kh.edu.istad.ite.features.admin.dto.response.BusinessFeatureResponse;
import kh.edu.istad.ite.features.admin.service.AdminFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/businesses/{businessId}/features")
@RequiredArgsConstructor
public class AdminFeatureController {

    private final AdminFeatureService adminFeatureService;

    @GetMapping
    public List<BusinessFeatureResponse> findFeatures(@PathVariable UUID businessId) {
        return adminFeatureService.findFeatures(businessId);
    }

    // Returns the full set afterwards, so the screen refreshes from one call.
    @PatchMapping
    public List<BusinessFeatureResponse> toggleFeature(
            @PathVariable UUID businessId,
            @Valid @RequestBody FeatureToggleRequest request
    ) {
        return adminFeatureService.toggleFeature(businessId, request);
    }
}
