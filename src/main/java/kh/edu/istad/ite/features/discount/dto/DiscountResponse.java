package kh.edu.istad.ite.features.discount.dto;

import kh.edu.istad.ite.shared.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DiscountResponse(
        UUID id,
        UUID businessId,
        String name,
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
        List<String> selectedDay,
        List<OrderChannel> applicableChannels,
        List<DiscountTargetResponse> targets,
        RecordStatus status
) {
}
