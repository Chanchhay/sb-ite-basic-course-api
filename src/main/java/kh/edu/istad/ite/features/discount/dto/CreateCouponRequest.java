package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.CouponStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateCouponRequest(
        @NotNull(message = "discountId cannot be null")
        UUID discountId,

        @NotBlank(message = "code cannot be empty")
        @Size(max = 60, message = "code must be at most 60 characters")
        String code,

        @Positive(message = "usageLimit must be positive")
        Integer usageLimit,

        @Positive(message = "usageLimitPerCustomer must be positive")
        Integer usageLimitPerCustomer,

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
