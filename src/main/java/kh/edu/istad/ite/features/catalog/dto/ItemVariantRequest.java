package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ItemVariantRequest(
        @NotBlank(message = "variant name cannot be empty")
        @Size(max = 150, message = "variant name must be at most 150 characters")
        String name,

        @DecimalMin(value = "0.0", inclusive = true, message = "variant price must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "variant price must have at most 10 integer digits and 2 decimal places")
        BigDecimal price
) {
}
