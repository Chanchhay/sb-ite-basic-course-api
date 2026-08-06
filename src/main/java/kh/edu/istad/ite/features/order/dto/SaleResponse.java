package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        UUID orderId,
        String invoiceNumber,
        UUID cashierId,
        OrderChannel channel,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal changeAmount,
        BigDecimal totalCost,
        String currency,
        /** The second currency this sale was shown in, frozen at sale time. */
        String displayCurrency,
        BigDecimal displayExchangeRate,
        PaymentMethodType paymentMethod,
        Integer itemCount,
        String note,
        LocalDateTime soldAt
) {
}
