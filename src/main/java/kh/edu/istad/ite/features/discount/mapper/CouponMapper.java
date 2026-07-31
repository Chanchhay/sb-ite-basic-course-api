package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.entity.Coupon;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

    public CouponResponse toResponse(Coupon coupon) {
        if (coupon == null) {
            return null;
        }

        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getUsageLimit(),
                coupon.getUsageLimitPerCustomer(),
                coupon.getUsedCount(),
                coupon.getMinPurchaseAmount(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getStatus(),
                coupon.getBusiness() == null
                        ? null
                        : coupon.getBusiness().getId(),
                coupon.getDiscount() == null
                        ? null
                        : coupon.getDiscount().getId()
        );
    }
}
