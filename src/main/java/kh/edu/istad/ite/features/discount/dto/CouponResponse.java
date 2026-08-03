package kh.edu.istad.ite.features.discount.dto;

import kh.edu.istad.ite.shared.enums.CouponStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        DiscountSummaryResponse discount,
        Integer usageLimit,
        Integer usageLimitPerCustomer,
        Integer usedCount,
        BigDecimal minPurchaseAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        CouponStatus status,
        UUID businessOwnerId,
        UUID discountId

) {
}
