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
        String discountLabel,
        BigDecimal total,
        String currency,
        /** The second currency this order was shown in, frozen at checkout. */
        String displayCurrency,
        BigDecimal displayExchangeRate,
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
            BigDecimal discountAmount,
            BigDecimal lineTotal,
            /**
             * The options this line was ordered with — "Sugar Level: 50%".
             * Already rendered for printing, since a receipt has no use for
             * the stored value once the order exists.
             */
            List<String> selections
    ) {}
}
