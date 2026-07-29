package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.*;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DiscountService {

    DiscountResponse createDiscount(UUID businessId, CreateDiscountRequest request);

//    Page<DiscountResponse> searchDiscounts(
//            UUID businessId,
//            String keyword,
//            DiscountType type,
//            DiscountRuleType ruleType,
//            DiscountScope scope,
//            RecordStatus status,
//            LocalDateTime activeAt,
//            Pageable pageable
//    );

    DiscountResponse getDiscountById(UUID businessId, UUID id);
    Page<DiscountResponse> getAllDiscounts(UUID businessId, Pageable pageable);
    DiscountResponse updateDiscount(UUID businessId, UUID id, CreateDiscountRequest request);
    DiscountResponse patchDiscount(UUID businessId, UUID id, PatchDiscountRequest request);
    void deleteDiscount(UUID businessId, UUID id);
}
