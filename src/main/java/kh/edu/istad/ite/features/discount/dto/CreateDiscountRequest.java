package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.*;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateDiscountRequest(
        @NotBlank(message = "name cannot be empty")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,
        @Size( max = 2000 )
        String description,
        @NotNull(message = "type is required")
        DiscountType type,
        @NotNull(message = "ruleType is required")
        DiscountRuleType ruleType,
        @Positive(message = "buyQuantity must be greater than 0")
        @Max(10000)
        Integer buyQuantity,
        @Positive(message = "getQuantity must be greater than 0")
        @Max(10000)
        Integer getQuantity,
        @Positive(message = "minQuantity must be greater than 0")
        @Max(10000)
        Integer minQuantity,
        @DecimalMin(value = "0.0", inclusive = false, message = "value must be greater than 0")
        @Digits(integer = 10, fraction = 2)
        BigDecimal value,
        @NotNull(message = "scope is required")
        DiscountScope scope,
        @DecimalMin(value = "0.0", message = "minOrderAmount cannot be negative")
        @Digits(integer = 10, fraction = 2)
        BigDecimal minOrderAmount,
        @DecimalMin(value = "0.0", message = "maxDiscountAmount cannot be negative")
        @Digits(integer = 10, fraction = 2)
        BigDecimal maxDiscountAmount,
        @NotBlank(message = "requiresCoupon cannot be empty")
        Boolean requiresCoupon,
        @NotBlank
        LocalDateTime startsAt,
        @NotBlank
        LocalDateTime endsAt,
        @NotBlank
        List<String> selectedDays ,
        @NotBlank(message = "Input like ACTIVE || INACTIVE")
        String status
) {
}
