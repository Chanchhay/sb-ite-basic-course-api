package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;


public record CreateOrderItemRequest(
        @NotNull
        UUID itemId,

        UUID variantId,

        @NotNull
        @Positive
        Integer quantity
) {
}
