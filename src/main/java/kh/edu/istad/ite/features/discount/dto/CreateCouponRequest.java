package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.*;
import kh.edu.istad.ite.shared.enums.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateCouponRequest(
        @NotNull(message = "discountId cannot be null")
        UUID discountId,

        @NotBlank(message = "code cannot be empty")
        @Size(max = 60, message = "code must be at most 60 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "code can only contain letters, numbers, hyphens and underscores (no spaces or special characters)"
        )
        String code,

        @Positive(message = "usageLimit must be positive")
        @Min(value = 1, message = "usageLimit must be at least 1")
        @Max(value = 100, message = "usageLimit must be at most 100")
        Integer usageLimit,

        @Positive(message = "usageLimitPerCustomer must be positive")
        @Min(value = 1, message = "usageLimitPerCustomer must be at least 1")
        @Max(value = 100, message = "usageLimitPerCustomer must be at most 100")
        Integer usageLimitPerCustomer,

        @DecimalMin(value = "0.0", inclusive = true, message = "minPurchaseAmount must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "minPurchaseAmount must have at most 10 integer digits and 2 decimal places")
        BigDecimal minPurchaseAmount,
        @NotBlank(message = "startsAt cannot be empty")
        LocalDateTime startsAt,
        @NotBlank(message = "startsAt cannot be empty")
        LocalDateTime endsAt,
        @NotBlank(message = "startsAt cannot be empty")
        CouponStatus status
) {
}
