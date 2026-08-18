package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateOrderDiscountRequest(
        @PositiveOrZero
        BigDecimal discountAmount,

        UUID discountId,

        String discountCode
) {
}
