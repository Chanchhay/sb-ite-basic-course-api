package kh.edu.istad.ite.features.payment.dto;

import java.util.UUID;

public record MarkReceiptPrintedRequest(
        UUID deviceId
) {
}
