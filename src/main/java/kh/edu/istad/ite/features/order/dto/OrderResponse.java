package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID businessId,
        UUID customerId,
        String invoiceNumber,
        OrderChannel channel,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String currency,
        /** The second currency this order was shown in, frozen at creation. */
        String displayCurrency,
        BigDecimal displayExchangeRate,
        String note,
        List<OrderItemResponse> items,
        LocalDateTime createdDate
) {
}
