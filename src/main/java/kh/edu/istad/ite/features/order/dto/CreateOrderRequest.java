package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull
        OrderChannel channel,

        UUID customerId,

        @Valid
        List<CreateOrderItemRequest> items,

        @PositiveOrZero
        BigDecimal discountAmount,

        TaxInclusionType taxInclusionType,

        @PositiveOrZero
        BigDecimal taxRate,

        @PositiveOrZero
        BigDecimal taxAmount,

        String currency,

        String note
) {
}
