package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.request.FeatureToggleRequest;
import kh.edu.istad.ite.features.admin.dto.response.BusinessFeatureResponse;
import kh.edu.istad.ite.features.admin.service.AdminAuditLogService;
import kh.edu.istad.ite.features.admin.service.AdminFeatureService;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessFeatureFlag;
import kh.edu.istad.ite.features.business.repository.BusinessFeatureFlagRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminFeatureServiceImpl implements AdminFeatureService {

    private final BusinessRepository businessRepository;
    private final BusinessFeatureFlagRepository featureFlagRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<BusinessFeatureResponse> findFeatures(UUID businessId) {
        requireBusiness(businessId);
        return buildResponse(businessId);
    }

    @Override
    @Transactional
    public List<BusinessFeatureResponse> toggleFeature(UUID businessId, FeatureToggleRequest request) {
        Business business = requireBusiness(businessId);
        BusinessFeature feature = request.feature();
        boolean enabling = Boolean.TRUE.equals(request.enabled());

        // Switching something off is the consequential direction, so it has to
        // be explained. Turning it back on needs no justification.
        if (!enabling && !StringUtils.hasText(request.reason())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A reason is required when disabling a feature");
        }

        BusinessFeatureFlag flag = featureFlagRepository
                .findByBusinessIdAndFeature(businessId, feature)
                .orElseGet(() -> {
                    BusinessFeatureFlag created = new BusinessFeatureFlag();
                    created.setBusiness(business);
                    created.setFeature(feature);
                    return created;
                });

        boolean wasEnabled = flag.getId() == null || Boolean.TRUE.equals(flag.getEnabled());

        flag.setEnabled(enabling);
        flag.setDisabledReason(enabling ? null : request.reason().trim());
        flag.setDisabledBy(enabling ? null : AuthHelper.currentUserId());
        flag.setDisabledAt(enabling ? null : LocalDateTime.now());

        featureFlagRepository.save(flag);

        adminAuditLogService.recordStateChange(
                enabling ? AdminActionType.BUSINESS_FEATURE_ENABLED : AdminActionType.BUSINESS_FEATURE_DISABLED,
                AuditTargetType.BUSINESS_FEATURE,
                businessId,
                business.getDisplayName() + " · " + feature.getLabel(),
                wasEnabled ? "ENABLED" : "DISABLED",
                enabling ? "ENABLED" : "DISABLED",
                request.reason()
        );

        log.info("Super admin {} {} for business {}",
                enabling ? "enabled" : "disabled", feature, businessId);

        return buildResponse(businessId);
    }

    /**
     * Every feature appears, whether or not a row exists, so the screen shows a
     * complete picture rather than only what has been touched.
     */
    private List<BusinessFeatureResponse> buildResponse(UUID businessId) {
        Map<BusinessFeature, BusinessFeatureFlag> flags = featureFlagRepository
                .findAllByBusinessId(businessId).stream()
                .collect(Collectors.toMap(BusinessFeatureFlag::getFeature, Function.identity(), (a, b) -> a));

        return Arrays.stream(BusinessFeature.values())
                .map(feature -> {
                    BusinessFeatureFlag flag = flags.get(feature);

                    return new BusinessFeatureResponse(
                            feature,
                            feature.getLabel(),
                            feature.getDescription(),
                            flag == null || Boolean.TRUE.equals(flag.getEnabled()),
                            flag == null ? null : flag.getDisabledReason(),
                            flag == null ? null : flag.getDisabledBy(),
                            flag == null ? null : flag.getDisabledAt()
                    );
                })
                .toList();
    }

    private Business requireBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Business has not been found"));
    }
}
