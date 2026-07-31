package kh.edu.istad.ite.features.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddToCartRequest(
        @NotNull UUID businessId,
        @NotNull UUID itemId,
        UUID variantId,
        @NotNull @Min(1) Integer quantity
) {
}