package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateCouponRequest(

        @NotBlank(message = "Code is required")
        @Size(min = 3, max = 9, message = "Code must be between 3 and 9 characters")
        String code,

        @NotNull(message = "Usage limit Is required")
        @Positive(message = "Usage limit per customer is required ")
        Integer usageLimit,

        @NotNull(message = "Usage limit per customer is required")
        @Positive(message = "Usage limit per customer must be greater than 0")
        Integer usageLimitPerCustomer,

        @PositiveOrZero(message = "Used count cannot be negative")
        Integer usedCount,

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

        @NotNull(message = "Created by is required")
        @Positive(message = "Created by must be a positive number")
        BigInteger createBy,

        @NotNull(message = "Discount type is required")
        UUID discountId,

        @NotNull(message = "Business owner ID is required")
        UUID businessOwnerId

) {
}
