package kh.edu.istad.ite.features.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record KhqrPreviewRequest(
        @NotNull
        @Positive
        BigDecimal amount,
        String currency,

        String billNumber,

        String terminalLabel
) {
}
