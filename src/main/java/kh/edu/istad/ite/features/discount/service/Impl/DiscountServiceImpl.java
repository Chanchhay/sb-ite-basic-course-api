package kh.edu.istad.ite.features.discount.service.Impl;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.PatchDiscountRequest;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.mapper.DiscountMapper;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.discount.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final BusinessRepository businessRepository;
    private final DiscountMapper discountMapper;

    @Override
    @Transactional
    public DiscountResponse createDiscount(UUID businessId, CreateDiscountRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));

        Discount discount = discountMapper.toEntity(request);
        discount.setBusiness(business);

        return discountMapper.toResponse(discountRepository.save(discount));
    }

//    @Override
//    public Page<DiscountResponse> searchDiscounts(
//            UUID businessId,
//            String keyword,
//            DiscountType type,
//            DiscountRuleType ruleType,
//            DiscountScope scope,
//            RecordStatus status,
//            LocalDateTime activeAt,
//            Pageable pageable) {// adjust parameter types matching your interface declaration
//
//
//
//        return null;
//    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponse getDiscountById(UUID businessId, UUID id) {
        Discount discount = discountRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount not found"));
        return discountMapper.toResponse(discount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DiscountResponse> getAllDiscounts(UUID businessId, Pageable pageable) {
        return discountRepository.findByBusinessId(businessId, pageable)
                .map(discountMapper::toResponse);
    }

    @Override
    @Transactional
    public DiscountResponse updateDiscount(UUID businessId, UUID id, CreateDiscountRequest request) {
        Discount discount = discountRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount not found"));

        discountMapper.updateEntityFromRequest(request, discount);
        return discountMapper.toResponse(discountRepository.save(discount));
    }

    @Override
    @Transactional
    public DiscountResponse patchDiscount(UUID businessId, UUID id, PatchDiscountRequest request) {
        Discount discount = discountRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount not found"));

        discountMapper.patchEntityFromRequest(request, discount);
        return discountMapper.toResponse(discountRepository.save(discount));
    }

    @Override
    @Transactional
    public void deleteDiscount(UUID businessId, UUID id) {
        Discount discount = discountRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount not found"));
        discountRepository.delete(discount);
    }
}
