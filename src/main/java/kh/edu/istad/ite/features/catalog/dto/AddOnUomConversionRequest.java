package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AddOnUomConversionRequest(
        @NotNull(message = "conversion unitId cannot be null")
        UUID unitId,

        @NotNull(message = "conversion factor cannot be null")
        @DecimalMin(value = "0.0", inclusive = false, message = "conversion factor must be greater than zero")
        @Digits(integer = 12, fraction = 6, message = "conversion factor must have at most 12 integer digits and 6 decimal places")
        BigDecimal factor
) {
}
