package kh.edu.istad.ite.features.social.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


public interface TelegramCheckoutService {


    CheckoutDraft createCheckout(UUID businessId, UUID customerId);

    VerifyResult verifyAndSettle(UUID businessId, UUID orderId);

    void cancelCheckout(UUID businessId, UUID orderId);

    record CheckoutDraft(
            UUID orderId,
            String invoiceNumber,
            BigDecimal total,
            String currency,
            int itemCount,
            String qrPayload,
            String md5,
            byte[] qrPng,
            LocalDateTime expiresAt
    ) {
    }

    record VerifyResult(
            boolean paid,
            boolean expired,
            String message,
            String invoiceNumber,
            String receiptText
    ) {
    }
}