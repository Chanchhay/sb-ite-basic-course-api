package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The whole currency configuration in one request, applied atomically.
 *
 * The currency list is the desired end state: codes absent from it are
 * removed. Sending the parts separately makes the caller responsible for
 * ordering them so no intermediate state trips validation, which is the
 * service's job rather than the client's.
 */
public record UpdateBusinessCurrencyConfigurationRequest(
        @NotBlank(message = "baseCurrency cannot be empty")
        @Size(min = 3, max = 3, message = "baseCurrency must be exactly 3 characters")
        String baseCurrency,

        /** Optional; defaults to the base currency, meaning no second amount. */
        @Size(min = 3, max = 3, message = "displayCurrency must be exactly 3 characters")
        String displayCurrency,

        @NotEmpty(message = "currencies cannot be empty")
        @Valid
        List<CreateBusinessCurrencyRequest> currencies
) {
}
