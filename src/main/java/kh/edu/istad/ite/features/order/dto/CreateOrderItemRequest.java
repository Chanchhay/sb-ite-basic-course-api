package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;


public record CreateOrderItemRequest(
        @NotNull
        UUID itemId,

        UUID variantId,

        UUID unitId,

        List<UUID> addOnIds,

        @NotNull
        @Positive
        Integer quantity
) {
}
