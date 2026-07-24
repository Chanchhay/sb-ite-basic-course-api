package kh.edu.istad.ite.features.business.dto;

import java.util.List;

public record BusinessCurrencyConfigurationResponse(
        String baseCurrency,
        String displayCurrency,
        List<BusinessCurrencyResponse> currencies
) {
}
