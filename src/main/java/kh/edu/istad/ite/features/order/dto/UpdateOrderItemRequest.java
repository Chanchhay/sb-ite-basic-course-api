package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateOrderItemRequest(
        @Positive Integer quantity,
        @PositiveOrZero BigDecimal discountAmount
) {
}
