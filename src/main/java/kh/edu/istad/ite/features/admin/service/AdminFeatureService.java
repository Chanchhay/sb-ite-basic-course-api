package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.request.FeatureToggleRequest;
import kh.edu.istad.ite.features.admin.dto.response.BusinessFeatureResponse;

import java.util.List;
import java.util.UUID;

public interface AdminFeatureService {

    List<BusinessFeatureResponse> findFeatures(UUID businessId);

    List<BusinessFeatureResponse> toggleFeature(UUID businessId, FeatureToggleRequest request);
}
