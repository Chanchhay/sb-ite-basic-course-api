package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record AddOrderItemRequest(
        @NotNull UUID itemId,
        UUID variantId,
        @NotNull @Positive Integer quantity,
        BigDecimal discountAmount
) {
}
