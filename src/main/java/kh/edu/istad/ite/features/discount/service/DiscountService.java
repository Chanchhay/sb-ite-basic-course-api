package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountStatusRequest;
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

    Page<DiscountResponse> searchDiscounts(
            UUID businessId,
            String keyword,
            DiscountType type,
            DiscountRuleType ruleType,
            DiscountScope scope,
            RecordStatus status,
            LocalDateTime activeAt,
            Pageable pageable
    );

    DiscountResponse findDiscountById(UUID businessId, UUID discountId);

    DiscountResponse updateDiscount(UUID businessId, UUID discountId, UpdateDiscountRequest request);

    DiscountResponse updateDiscountStatus(UUID businessId, UUID discountId, UpdateDiscountStatusRequest request);

    void deleteDiscount(UUID businessId, UUID discountId);
}
