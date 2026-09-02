package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.request.BusinessStatusActionRequest;
import kh.edu.istad.ite.features.admin.dto.response.ProvinceBackfillResponse;
import kh.edu.istad.ite.features.admin.service.AdminAuditLogService;
import kh.edu.istad.ite.features.admin.service.AdminBusinessService;
import kh.edu.istad.ite.features.admin.specification.BusinessAdminSpecifications;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import kh.edu.istad.ite.shared.helper.CambodiaProvinceMatcher;
import kh.edu.istad.ite.shared.cache.BusinessCacheEvictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminBusinessServiceImpl implements AdminBusinessService {

    private static final String STATE_ENABLED = "ENABLED";
    private static final String STATE_DISABLED = "DISABLED";
    private static final String STATE_OPEN = "OPEN";
    private static final String STATE_CLOSED = "CLOSED";

    private final BusinessRepository businessRepository;
    private final BusinessMapper businessMapper;
    private final AdminAuditLogService adminAuditLogService;
    private final BusinessCacheEvictor businessCacheEvictor;

    @Override
    @Transactional(readOnly = true)
    public Page<BusinessResponse> getBusinesses(
            BusinessOwnerStatus status,
            Boolean isEnabled,
            Boolean isClosed,
            UUID categoryId,
            String keyword,
            Pageable pageable
    ) {
        var spec = BusinessAdminSpecifications.withFilters(status, isEnabled, isClosed, categoryId, keyword);
        return businessRepository.findAll(spec, pageable).map(businessMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessResponse getBusiness(UUID businessId) {
        return businessMapper.toResponse(findBusiness(businessId));
    }

    @Override
    @Transactional
    public BusinessResponse activate(UUID businessId) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = statusName(business);

        business.setStatus(BusinessOwnerStatus.ACTIVE);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_ACTIVATED, saved, previousState,
                BusinessOwnerStatus.ACTIVE.name(), null);

        log.info("Super admin activated business {}", businessId);
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse suspend(UUID businessId, BusinessStatusActionRequest request) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = statusName(business);

        business.setStatus(BusinessOwnerStatus.SUSPENDED);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_SUSPENDED, saved, previousState,
                BusinessOwnerStatus.SUSPENDED.name(), reasonOf(request));

        log.info("Super admin suspended business {} (reason: {})", businessId, reasonOf(request));
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse enable(UUID businessId) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = enabledState(business);

        business.setIsEnabled(true);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_ENABLED, saved, previousState, STATE_ENABLED, null);

        log.info("Super admin enabled business {}", businessId);
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse disable(UUID businessId, BusinessStatusActionRequest request) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = enabledState(business);

        business.setIsEnabled(false);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_DISABLED, saved, previousState, STATE_DISABLED, reasonOf(request));

        log.info("Super admin disabled business {} (reason: {})", businessId, reasonOf(request));
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse close(UUID businessId, BusinessStatusActionRequest request) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = closedState(business);

        business.setIsClosed(true);
        business.setIsListing(false);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_CLOSED, saved, previousState, STATE_CLOSED, reasonOf(request));

        log.info("Super admin closed business {} (reason: {})", businessId, reasonOf(request));
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse reopen(UUID businessId) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = closedState(business);

        business.setIsClosed(false);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_REOPENED, saved, previousState, STATE_OPEN, null);

        log.info("Super admin reopened business {}", businessId);
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse delete(UUID businessId) {
        businessCacheEvictor.evictStorefront(businessId);
        Business business = findBusiness(businessId);
        String previousState = statusName(business);

        business.setStatus(BusinessOwnerStatus.DELETED);
        business.setIsEnabled(false);
        business.setIsListing(false);
        Business saved = businessRepository.save(business);

        audit(AdminActionType.BUSINESS_DELETED, saved, previousState,
                BusinessOwnerStatus.DELETED.name(), null);

        log.info("Super admin deleted business {}", businessId);
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProvinceBackfillResponse backfillProvinceNames() {
        List<Business> candidates = businessRepository.findBackfillProvinceCandidates();
        List<ProvinceBackfillResponse.UnmatchedBusiness> unmatched = new ArrayList<>();
        int matchedCount = 0;

        for (Business business : candidates) {
            String matched = CambodiaProvinceMatcher.match(business.getCityOrProvince());

            if (matched == null) {
                unmatched.add(new ProvinceBackfillResponse.UnmatchedBusiness(
                        business.getId(), business.getDisplayName(), business.getCityOrProvince()));
                continue;
            }

            business.setProvinceName(matched);
            businessRepository.save(business);
            matchedCount++;

            adminAuditLogService.record(
                    AdminActionType.BUSINESS_PROVINCE_BACKFILLED,
                    AuditTargetType.BUSINESS,
                    business.getId(),
                    business.getDisplayName(),
                    "Auto-matched \"" + business.getCityOrProvince() + "\" -> \"" + matched + "\""
            );
        }

        log.info("Province backfill: matched {} of {} candidates", matchedCount, candidates.size());
        return new ProvinceBackfillResponse(matchedCount, unmatched.size(), unmatched);
    }

    private void audit(
            AdminActionType actionType,
            Business business,
            String previousState,
            String newState,
            String reason
    ) {
        adminAuditLogService.recordStateChange(
                actionType,
                AuditTargetType.BUSINESS,
                business.getId(),
                business.getDisplayName(),
                previousState,
                newState,
                reason
        );
    }

    private String reasonOf(BusinessStatusActionRequest request) {
        return request == null ? null : request.reason();
    }

    private String statusName(Business business) {
        return business.getStatus() == null ? null : business.getStatus().name();
    }

    private String enabledState(Business business) {
        return Boolean.TRUE.equals(business.getIsEnabled()) ? STATE_ENABLED : STATE_DISABLED;
    }

    private String closedState(Business business) {
        return Boolean.TRUE.equals(business.getIsClosed()) ? STATE_CLOSED : STATE_OPEN;
    }

    private Business findBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }
}
