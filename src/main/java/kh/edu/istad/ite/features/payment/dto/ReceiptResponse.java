package kh.edu.istad.ite.features.payment.dto;

import kh.edu.istad.ite.shared.enums.ReceiptType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        UUID orderId,
        String invoiceNumber,
        String vatNumber,
        ReceiptType type,
        String fileUrl,
        UUID deviceId,
        UUID printedBy,
        LocalDateTime printedAt,
        LocalDateTime issuedAt
) {
}
