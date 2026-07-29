package kh.edu.istad.ite.features.discount.dto;

import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PatchDiscountRequest(

        @Size(max = 150) String name,
        String description,
        DiscountType type,
        DiscountRuleType ruleType,
        Integer buyQuantity,
        Integer getQuantity,
        Integer minQuantity,
        BigDecimal value,
        DiscountScope scope,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Boolean requiresCoupon,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String selectedDay,
        RecordStatus status

) {
}
