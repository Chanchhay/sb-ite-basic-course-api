package kh.edu.istad.ite.features.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KhqrResponse(
        String qr,
        String md5,
        BigDecimal amount,
        String currency,
        String billNumber,
        LocalDateTime expiresAt
) {
}
