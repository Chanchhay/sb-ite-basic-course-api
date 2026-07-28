package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;


public record ModifierSelectionRequest(

        String groupName,

        @NotBlank
        String name,

        @NotNull
        @PositiveOrZero
        BigDecimal unitPrice,

        @NotNull
        @Positive
        Integer quantity
) {
}