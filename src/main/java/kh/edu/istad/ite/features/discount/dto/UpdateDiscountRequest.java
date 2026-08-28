package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.*;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UpdateDiscountRequest(
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Pattern(regexp = "PERCENTAGE|FIXED_AMOUNT|BUY_X_GET_Y", message = "type must be one of: PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y")
        String type,

        @Pattern(regexp = "NO_CONDITION|MIN_QUANTITY|MIN_ORDER_AMOUNT|BUY_X_GET_Y", message = "ruleType must be one of: NO_CONDITION, MIN_QUANTITY, MIN_ORDER_AMOUNT, BUY_X_GET_Y")
        String ruleType,

        @Min(value = 1, message = "buyQuantity must be at least 1")
        @Max(value = 1000, message = "buyQuantity must be at most 1000 Integer buyQuantity")
        Integer buyQuantity,

        @Min(value = 1, message = "getQuantity must be at least 1")
        @Max(value = 1000, message = "getQuantity must be at most 1000")
        Integer getQuantity,

        @Min(value = 1, message = "minQuantity must be at least 1")
        @Max(value = 1000, message = "minQuantity must be at most 1000")
        Integer minQuantity,

        @DecimalMin(value = "0.0", inclusive = true, message = "value cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "value must have at most 10 integer digits and 2 decimal places")
        BigDecimal value,

        DiscountScope scope,

        @DecimalMin(value = "0.0", inclusive = true,message = "minOrderAmount must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "minOrderAmount must have at most 10 integer digits and 2 decimal places")
        BigDecimal minOrderAmount,

        @DecimalMin(value = "0.0", inclusive = true, message = "maxDiscountAmount must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "maxDiscountAmount must have at most 10 integer digits and 2 decimal places")
        BigDecimal maxDiscountAmount,

        Boolean requiresCoupon,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        @Size(max = 7, message = "selectedDays cannot contain more than 7 entries")
        List<DayOfWeek> selectedDays,

        @Pattern(regexp = "ACTIVE|INACTIVE", message = "status must be one of: ACTIVE, INACTIVE")
        String status,

        // Null = don't touch channel restriction. Pass an empty list to clear
        // it back to "all channels".
        List<OrderChannel> applicableChannels,

        // Null = don't touch item targets. Pass an empty list to clear all
        // item targets (only meaningful while scope = ITEM).
        @Size(max = 200, message = "targetItemIds cannot contain more than 200 entries")
        List<UUID> targetItemIds,

        //Null = don't touch category targets. pass an empty list to clear
        // all category targets (only meaningful while scope = CATEGORY ).
        @Size(max = 200, message = "targetItemGroupIds cannot contain more than 200 entries")
        List<UUID> targetItemGroupIds,

        // Optional: when activating or updating scope = ALL_ITEMS / ORDER, if true, pauses other active discounts
        Boolean pauseOtherDiscounts
) {
}
