package kh.edu.istad.ite.features.payment.bakong;

import java.math.BigDecimal;

public record BakongCheckResult(
        boolean paid,

        boolean verificationFailed,
        String message,
        String hash,
        String fromAccountId,
        BigDecimal amount,
        String currency,
        Long acknowledgedDateMs
) {

    public static BakongCheckResult notPaid(String message) {
        return new BakongCheckResult(false, false, message, null, null, null, null, null);
    }

    public static BakongCheckResult unverifiable(String message) {
        return new BakongCheckResult(false, true, message, null, null, null, null, null);
    }
}