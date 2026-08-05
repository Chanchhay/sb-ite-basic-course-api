package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.util.List;
import java.util.UUID;

public interface DiscountService {

    DiscountResponse createDiscount(UUID businessId, CreateDiscountRequest request);

    List<DiscountResponse> findAllDiscounts(UUID businessId);

    DiscountResponse findDiscountById(UUID businessId, UUID discountId);

    DiscountResponse updateDiscount(UUID businessId, UUID discountId, UpdateDiscountRequest request);

    DiscountResponse activateDiscount(UUID businessId, UUID discountId);

    DiscountResponse deactivateDiscount(UUID businessId, UUID discountId);

    void deleteDiscount(UUID businessId, UUID discountId);

    // Resolves which discounts are currently usable for a checkout happening
    // on the given channel, optionally scoped to one item and/or one
    // category. Also filters by status = ACTIVE, the starts_at/ends_at
    // window, and selected_days (if set).
    List<DiscountResponse> findApplicableDiscounts(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId
    );

}
