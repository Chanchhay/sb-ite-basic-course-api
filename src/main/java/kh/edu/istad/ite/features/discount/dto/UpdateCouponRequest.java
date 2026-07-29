package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateCouponRequest(

        @NotBlank(message = "Coupon code is required")
        @Size(min = 3, max = 50, message = "Coupon code must be between 3 and 50 characters")
        String code,

        @NotNull(message = "Usage limit is required")
        @Positive(message = "Usage limit must be greater than 0")
        Integer usageLimit,

        @NotNull(message = "Usage limit per customer is required")
        @Positive(message = "Usage limit per customer must be greater than 0")
        Integer usageLimitPerCustomer,

        @NotNull(message = "Minimum purchase amount is required")
        @PositiveOrZero(message = "Minimum purchase amount cannot be negative")
        Double minPurchaseAmount,

        @NotNull(message = "Start date is required")
        LocalDateTime startsAt,

        @NotNull(message = "End date is required")
        LocalDateTime endsAt,

        @NotBlank(message = "Status is required")
        @Pattern(
                regexp = "ACTIVE|INACTIVE|EXPIRED",
                message = "Status must be ACTIVE, INACTIVE, or EXPIRED"
        )
        String status,

        @NotNull(message = "Discount ID is required")
        UUID discountId,

        @NotNull(message = "Business ID is required")
        UUID businessId


) {
}
