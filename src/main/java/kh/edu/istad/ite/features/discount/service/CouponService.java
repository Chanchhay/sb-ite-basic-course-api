package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.dto.CreateCouponRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateCouponRequest;

import java.util.List;
import java.util.UUID;

public interface CouponService {

    CouponResponse createCoupon(UUID businessId, CreateCouponRequest request);

    List<CouponResponse> findAllCoupons(UUID businessId, UUID discountId);

    CouponResponse findCouponById(UUID businessId, UUID couponId);

    CouponResponse findCouponByCode(UUID businessId, String code);

    CouponResponse updateCoupon(UUID businessId, UUID couponId, UpdateCouponRequest request);

    CouponResponse activateCoupon(UUID businessId, UUID couponId);

    CouponResponse deactivateCoupon(UUID businessId, UUID couponId);

    void deleteCoupon(UUID businessId, UUID couponId);
}
