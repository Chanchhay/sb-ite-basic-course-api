package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.request.PlatformFeatureToggleRequest;
import kh.edu.istad.ite.features.admin.dto.response.PlatformFeatureResponse;
import kh.edu.istad.ite.features.admin.entity.PlatformFeatureFlag;
import kh.edu.istad.ite.features.admin.repository.PlatformFeatureFlagRepository;
import kh.edu.istad.ite.features.admin.service.AdminAuditLogService;
import kh.edu.istad.ite.features.admin.service.AdminPlatformFeatureService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminPlatformFeatureServiceImpl implements AdminPlatformFeatureService {

    private final PlatformFeatureFlagRepository platformFeatureFlagRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<PlatformFeatureResponse> findFeatures() {
        return buildResponse();
    }

    @Override
    @Transactional
    public List<PlatformFeatureResponse> toggleFeature(BusinessFeature feature, PlatformFeatureToggleRequest request) {
        boolean enabling = Boolean.TRUE.equals(request.enabled());

        // Switching something off is the consequential direction, so it has to
        // be explained. Turning it back on needs no justification.
        if (!enabling && !StringUtils.hasText(request.reason())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A reason is required when disabling a feature");
        }

        PlatformFeatureFlag flag = platformFeatureFlagRepository
                .findByFeature(feature)
                .orElseGet(() -> {
                    PlatformFeatureFlag created = new PlatformFeatureFlag();
                    created.setFeature(feature);
                    return created;
                });

        boolean wasEnabled = Boolean.TRUE.equals(flag.getEnabled());

        flag.setEnabled(enabling);
        flag.setDisabledReason(enabling ? null : request.reason().trim());
        flag.setDisabledBy(enabling ? null : AuthHelper.currentUserId());
        flag.setDisabledAt(enabling ? null : LocalDateTime.now());

        platformFeatureFlagRepository.save(flag);

        adminAuditLogService.recordStateChange(
                enabling ? AdminActionType.PLATFORM_FEATURE_ENABLED : AdminActionType.PLATFORM_FEATURE_DISABLED,
                AuditTargetType.PLATFORM_FEATURE,
                targetIdFor(feature),
                "Platform · " + feature.getLabel(),
                wasEnabled ? "ENABLED" : "DISABLED",
                enabling ? "ENABLED" : "DISABLED",
                request.reason()
        );

        log.info("Super admin {} {} platform-wide", enabling ? "enabled" : "disabled", feature);

        return buildResponse();
    }

    /**
     * Every feature appears, whether or not a row exists, so the screen shows a
     * complete picture rather than only what has been touched.
     */
    private List<PlatformFeatureResponse> buildResponse() {
        Map<BusinessFeature, PlatformFeatureFlag> flags = platformFeatureFlagRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformFeatureFlag::getFeature, Function.identity(), (a, b) -> a));

        return Arrays.stream(BusinessFeature.values())
                .map(feature -> {
                    PlatformFeatureFlag flag = flags.get(feature);

                    return new PlatformFeatureResponse(
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

    /** Audit log target_id is non-null; a platform-wide switch has no business id to use instead. */
    private UUID targetIdFor(BusinessFeature feature) {
        return UUID.nameUUIDFromBytes(("platform-feature:" + feature.name()).getBytes(StandardCharsets.UTF_8));
    }
}
