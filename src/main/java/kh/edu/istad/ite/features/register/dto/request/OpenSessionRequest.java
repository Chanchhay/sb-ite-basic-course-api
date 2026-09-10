package kh.edu.istad.ite.features.register.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenSessionRequest {
    @NotNull(message = "Opening balance is required")
    @PositiveOrZero(message = "Opening balance must be zero or positive")
    private BigDecimal openingBalance;

    private BigDecimal baseOpeningBalance;

    private String secondaryCurrency;

    private BigDecimal secondaryOpeningBalance;

    private BigDecimal secondaryExchangeRate;

    private String note;
}
