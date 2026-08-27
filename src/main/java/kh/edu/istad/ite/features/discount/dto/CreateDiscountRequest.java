package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.*;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateDiscountRequest(
        @NotBlank(message = "name cannot be empty")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size( max = 2000 ,message = "description must be at ost 2000 characters" )
        String description,

        @NotNull(message = "type cannot be null")
        DiscountType type,

        @NotNull(message = "ruleType cannot be null")
        DiscountRuleType ruleType,

        @Positive(message = "buyQuantity must be greater than 0")
        @Max(10000)
        @Min(value = 1, message = "getQuantity must be at least 1")
        Integer buyQuantity,

        @Positive(message = "getQuantity must be greater than 0")
        @Max(10000)
        Integer getQuantity,

        @Positive(message = "minQuantity must be greater than 0")
        @Max(10000)
        @Min(value = 1, message = "minQuantity must be at least 1")
        Integer minQuantity,

        @NotNull(message = "value cannot be null")
        @DecimalMin(value = "0.0", inclusive = true, message = "value cannot be negative")
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

        @NotNull(message = "requiresCoupon cannot be null")
        Boolean requiresCoupon,

        @NotNull(message = "startsAt cannot be null")
        LocalDateTime startsAt,

        @NotNull(message = "endsAt cannot be null")
        LocalDateTime endsAt,

        @Size(max = 7, message = "selectedDays cannot contain more than  7 entries")
        List<DayOfWeek> selectedDays ,

        @NotNull(message = "status cannot be null")
        String status,


        // Which order channels this discount is allowed on,
        // Null/empty = applies everywhere (POS, WEB , TELEGRAM, MESSENGER).
        // e.g. [WEB] -> only on the website; [POS] -> only in the physical shop.
        @Size(max = 4, message = "applicableChannels cannot contain more than 4 entries")
        List<OrderChannel> applicableChannels,

        // Required when scope = ITEM: the specific product(s) this discount applies to.
        @Size(max = 200, message = "targetItemIds cannot contain more than 200 entries")
        List<UUID> targetItemIds,

        // Required when scope = CATEGORY: the specific category/categories
        // (item groups, e.g. "Food", "Drink") this discount applies to.
        @Size(max = 200, message = "targetItemGroupIds cannot contain more than 200 entries")
        List<UUID> targetItemGroupIds
) {
}
