package kh.edu.istad.ite.features.cart.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StorefrontCheckoutResponse(
        UUID orderId,
        String invoiceNumber,
        UUID businessId,
        String storeName,
        String storeSlug,
        int itemCount,
        BigDecimal total,
        String currency,
        /** The second currency this order is shown in, frozen at checkout — null when the shop shows only one currency. */
        String displayCurrency,
        /** Units of displayCurrency per one unit of currency. */
        BigDecimal displayExchangeRate,
        String qr,
        String md5,
        String qrImage,
        LocalDateTime expiresAt
) {
}