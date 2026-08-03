package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.dto.DiscountSummaryResponse;
import kh.edu.istad.ite.features.discount.entity.Coupon;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

    public CouponResponse toResponse(Coupon coupon) {
        if (coupon == null) {
            return null;
        }

        DiscountSummaryResponse discountSummary = null;

        if(coupon.getDiscount() != null){

            discountSummary = new DiscountSummaryResponse(
                    coupon.getDiscount().getId(),
                    coupon.getDiscount().getName(),
                    coupon.getDiscount().getType(),
                    coupon.getDiscount().getScope(),
                    coupon.getDiscount().getValue()// adjust to your actual field
            );

        }

        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                discountSummary,
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
