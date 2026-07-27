package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull
        OrderChannel channel,

        UUID customerId,

        @NotEmpty
        @Valid
        List<CreateOrderItemRequest> items,

        @PositiveOrZero
        BigDecimal discountAmount,

        String currency,

        String note
) {
}
