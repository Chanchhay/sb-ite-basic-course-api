package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateBusinessCurrencyRequest(
        @NotBlank(message = "code cannot be empty")
        @Size(min = 3, max = 3, message = "code must be exactly 3 characters")
        String code,

        @NotBlank(message = "name cannot be empty")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "symbol cannot be empty")
        @Size(max = 5, message = "symbol must be at most 5 characters")
        String symbol,

        @NotNull(message = "exchangeRate cannot be null")
        @DecimalMin(value = "0.0", inclusive = false, message = "exchangeRate must be greater than zero")
        BigDecimal exchangeRate,

        @NotNull(message = "decimalPlaces cannot be null")
        @Min(value = 0, message = "decimalPlaces must be at least 0")
        @Max(value = 3, message = "decimalPlaces must be at most 3")
        Short decimalPlaces
) {
}
