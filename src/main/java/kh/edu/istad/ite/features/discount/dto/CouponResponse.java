package kh.edu.istad.ite.features.discount.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        Integer usageLimit,
        Integer usageLimitPerCustomer,
        Integer usedCount,
        Double minPurchaseAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        BigInteger createBy,

        UUID discountId,
        String discountName,

        UUID businessOwnerId,
        String businessName

) {
}
