package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.request.PlatformFeatureToggleRequest;
import kh.edu.istad.ite.features.admin.dto.response.PlatformFeatureResponse;
import kh.edu.istad.ite.shared.enums.BusinessFeature;

import java.util.List;

public interface AdminPlatformFeatureService {

    List<PlatformFeatureResponse> findFeatures();

    List<PlatformFeatureResponse> toggleFeature(BusinessFeature feature, PlatformFeatureToggleRequest request);
}
