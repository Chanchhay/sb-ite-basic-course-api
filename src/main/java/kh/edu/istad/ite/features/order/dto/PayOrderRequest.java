package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;

import java.math.BigDecimal;

public record PayOrderRequest(
        @NotNull
        PaymentMethodType paymentMethod,

        @PositiveOrZero
        BigDecimal receivedAmount,

        TaxInclusionType taxInclusionType,

        String note
) {
}
