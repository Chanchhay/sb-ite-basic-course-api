package kh.edu.istad.ite.features.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OfflineOrderDto(
        String uuid,
        OrderChannel channel,
        OrderStatus status,
        BigDecimal subtotal,

        @JsonProperty("discount_amount")
        BigDecimal discountAmount,

        BigDecimal total,

        @JsonProperty("payment_method")
        PaymentMethodType paymentMethod,

        @JsonProperty("created_at")
        Instant createdAt,

        List<OfflineOrderItemDto> items
) {}
