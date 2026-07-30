package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.UpdateDiscountRequest;

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
}
