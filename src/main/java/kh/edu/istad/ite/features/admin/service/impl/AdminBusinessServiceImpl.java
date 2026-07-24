package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.request.BusinessStatusActionRequest;
import kh.edu.istad.ite.features.admin.service.AdminBusinessService;
import kh.edu.istad.ite.features.admin.specification.BusinessAdminSpecifications;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminBusinessServiceImpl implements AdminBusinessService {

    private final BusinessRepository businessRepository;
    private final BusinessMapper businessMapper;

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
        Business business = findBusiness(businessId);
        business.setStatus(BusinessOwnerStatus.ACTIVE);
        log.info("Super admin activated business {}", businessId);
        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse suspend(UUID businessId, BusinessStatusActionRequest request) {
        Business business = findBusiness(businessId);
        business.setStatus(BusinessOwnerStatus.SUSPENDED);
        log.info("Super admin suspended business {} (reason: {})", businessId,
                request == null ? null : request.reason());
        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse enable(UUID businessId) {
        Business business = findBusiness(businessId);
        business.setIsEnabled(true);
        log.info("Super admin enabled business {}", businessId);
        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse disable(UUID businessId, BusinessStatusActionRequest request) {
        Business business = findBusiness(businessId);
        business.setIsEnabled(false);
        log.info("Super admin disabled business {} (reason: {})", businessId,
                request == null ? null : request.reason());
        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse close(UUID businessId, BusinessStatusActionRequest request) {
        Business business = findBusiness(businessId);
        business.setIsClosed(true);
        business.setIsListing(false);
        log.info("Super admin closed business {} (reason: {})", businessId,
                request == null ? null : request.reason());
        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse reopen(UUID businessId) {
        Business business = findBusiness(businessId);
        business.setIsClosed(false);
        log.info("Super admin reopened business {}", businessId);
        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse delete(UUID businessId) {
        Business business = findBusiness(businessId);
        business.setStatus(BusinessOwnerStatus.DELETED);
        business.setIsEnabled(false);
        business.setIsListing(false);
        log.info("Super admin deleted business {}", businessId);
        return businessMapper.toResponse(businessRepository.save(business));
    }

    private Business findBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }
}
