package kh.edu.istad.ite.features.payment.bakong;

import java.math.BigDecimal;


public record BakongCheckResult(
        boolean paid,
        String message,
        String hash,
        String fromAccountId,
        BigDecimal amount,
        String currency,
        Long acknowledgedDateMs
) {

    public static BakongCheckResult notPaid(String message) {
        return new BakongCheckResult(false, message, null, null, null, null, null);
    }
}
