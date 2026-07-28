package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * All fields are optional; only non-null fields are applied (partial update),
 * following the same convention as UpdateItemRequest / UpdateItemGroupRequest.
 */
public record UpdateDiscountRequest(
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        String description,

        DiscountType type,

        DiscountRuleType ruleType,

        @Positive(message = "buyQuantity must be greater than 0")
        Integer buyQuantity,

        @Positive(message = "getQuantity must be greater than 0")
        Integer getQuantity,

        @Positive(message = "minQuantity must be greater than 0")
        Integer minQuantity,

        @DecimalMin(value = "0.0", inclusive = false, message = "value must be greater than 0")
        BigDecimal value,

        DiscountScope scope,

        @DecimalMin(value = "0.0", message = "minOrderAmount cannot be negative")
        BigDecimal minOrderAmount,

        @DecimalMin(value = "0.0", message = "maxDiscountAmount cannot be negative")
        BigDecimal maxDiscountAmount,

        Boolean requiresCoupon,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        UUID branchId
) {
}
