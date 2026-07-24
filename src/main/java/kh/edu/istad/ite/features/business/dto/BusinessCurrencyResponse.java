package kh.edu.istad.ite.features.business.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BusinessCurrencyResponse(
        UUID id,
        String code,
        String name,
        BigDecimal exchangeRate,
        String symbol,
        Short decimalPlaces,
        Boolean baseCurrency,
        Boolean displayCurrency
) {
}
