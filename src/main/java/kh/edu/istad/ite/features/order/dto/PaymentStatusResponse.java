package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.QrStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID orderId,
        OrderStatus orderStatus,
        QrStatus qrStatus,
        boolean paid,
        String message,
        LocalDateTime expiresAt,
        LocalDateTime paidAt
) {
}
