package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;


public record CreateOrderItemRequest(
        @NotNull
        UUID itemId,

        UUID variantId,

        @NotNull
        @Positive
        Integer quantity,

        @Valid
        List<ModifierSelectionRequest> modifiers
) {
}
