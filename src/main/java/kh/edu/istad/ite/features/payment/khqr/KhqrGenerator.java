package kh.edu.istad.ite.features.payment.khqr;

import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;

import java.math.BigDecimal;
import java.time.Instant;

public interface KhqrGenerator {

    Result generate(
            BusinessPaymentSetting setting,
            BigDecimal amount,
            String currency,
            String billNumber,
            String terminalLabel,
            Instant expiresAt
    );

    record Result(String qr, String md5) {
    }
}
