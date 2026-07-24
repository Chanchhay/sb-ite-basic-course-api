package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateBusinessCurrencyRequest(
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 5, message = "symbol must be at most 5 characters")
        String symbol,

        @DecimalMin(value = "0.0", inclusive = false, message = "exchangeRate must be greater than zero")
        BigDecimal exchangeRate,

        @Min(value = 0, message = "decimalPlaces must be at least 0")
        @Max(value = 3, message = "decimalPlaces must be at most 3")
        Short decimalPlaces
) {
}
