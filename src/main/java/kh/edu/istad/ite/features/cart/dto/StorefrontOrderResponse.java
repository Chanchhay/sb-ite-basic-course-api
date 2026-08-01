package kh.edu.istad.ite.features.cart.dto;

import kh.edu.istad.ite.shared.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StorefrontOrderResponse(
        UUID orderId,
        String invoiceNumber,
        UUID businessId,
        String storeName,
        String storeSlug,
        String storeLogo,
        String storeAddress,
        String storePhone,
        String customerName,
        String customerEmail,
        String customerPhone,
        OrderStatus status,
        String channel,
        String paymentMethod,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String currency,
        int itemCount,
        LocalDateTime createdDate,
        LocalDateTime paidAt,
        List<StorefrontOrderItemResponse> items
) {
    public record StorefrontOrderItemResponse(
            UUID itemId,
            String itemName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
