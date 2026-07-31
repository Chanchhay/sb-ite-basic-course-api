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
        String qr,
        String md5,
        String qrImage,
        LocalDateTime expiresAt
) {
}