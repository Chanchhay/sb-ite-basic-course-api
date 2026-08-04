package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UpdateDiscountRequest(
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        String description,

        @Pattern(regexp = "PERCENTAGE|FIXED_AMOUNT|BUY_X_GET_Y", message = "type must be one of: PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y")
        String type,

        @Pattern(regexp = "NO_CONDITION|MIN_QUANTITY|MIN_ORDER_AMOUNT|BUY_X_GET_Y", message = "ruleType must be one of: NO_CONDITION, MIN_QUANTITY, MIN_ORDER_AMOUNT, BUY_X_GET_Y")
        String ruleType,

        Integer buyQuantity,

        Integer getQuantity,

        Integer minQuantity,

        @Digits(integer = 10, fraction = 2, message = "value must have at most 10 integer digits and 2 decimal places")
        BigDecimal value,

        @Pattern(regexp = "ALL_ITEMS|SPECIFIC_ITEMS|SPECIFIC_CATEGORIES|SPECIFIC_MEMBERSHIP", message = "scope must be one of: ALL_ITEMS, SPECIFIC_ITEMS, SPECIFIC_CATEGORIES, SPECIFIC_MEMBERSHIP")
        String scope,

        @Digits(integer = 10, fraction = 2, message = "minOrderAmount must have at most 10 integer digits and 2 decimal places")
        BigDecimal minOrderAmount,

        @Digits(integer = 10, fraction = 2, message = "maxDiscountAmount must have at most 10 integer digits and 2 decimal places")
        BigDecimal maxDiscountAmount,

        Boolean requiresCoupon,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        List<String> selectedDays,

        @Pattern(regexp = "ACTIVE|INACTIVE", message = "status must be one of: ACTIVE, INACTIVE")
        String status,

        // Null = don't touch channel restriction. Pass an empty list to clear
        // it back to "all channels".
        List<OrderChannel> applicableChannels,

        // Null = don't touch item targets. Pass an empty list to clear all
        // item targets (only meaningful while scope = ITEM).
        List<UUID> targetItemIds,

        //Null = don't touch category targets. pass an empty list to clear
        // all category targets (only meaningful while scope = CATEGORY ).

        List<UUID> targetItemGroupIds
) {
}
