package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

public record UpdateCouponRequest(
        @Size(max = 60, message = "code must be at most 60 characters")
        String code,

        @Positive(message = "usageLimit must be positive")
        Integer usageLimit,

        @Positive(message = "usageLimitPerCustomer must be positive")
        Integer usageLimitPerCustomer,

        @Digits(integer = 10, fraction = 2, message = "minPurchaseAmount must have at most 10 integer digits and 2 decimal places")
        BigDecimal minPurchaseAmount,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        @Pattern(regexp = "ACTIVE|INACTIVE|EXPIRED|USED_UP", message = "status must be one of: ACTIVE, INACTIVE, EXPIRED, USED_UP")
        String status
) {
}
