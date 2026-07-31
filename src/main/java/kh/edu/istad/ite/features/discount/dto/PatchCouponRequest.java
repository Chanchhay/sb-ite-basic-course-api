package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record PatchCouponRequest(

        @Size(min = 3, max = 50, message = "Coupon code must be between 3 and 50 characters")
        String code,

        @Positive(message = "Usage limit must be greater than 0")
        Integer usageLimit,

        @Positive(message = "Usage limit per customer must be greater than 0")
        Integer usageLimitPerCustomer,

        @PositiveOrZero(message = "Minimum purchase amount cannot be negative")
        Double minPurchaseAmount,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        @Pattern(
                regexp = "ACTIVE|INACTIVE|EXPIRED",
                message = "Status must be ACTIVE, INACTIVE, or EXPIRED"
        )
        String status,

        UUID discountId,

        UUID businessId

) {
}
