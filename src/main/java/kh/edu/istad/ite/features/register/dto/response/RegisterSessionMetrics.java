package kh.edu.istad.ite.features.register.dto.response;

import java.math.BigDecimal;


public record RegisterSessionMetrics(
        long activeCount,
        BigDecimal totalOpening,
        BigDecimal totalCashSales,
        BigDecimal totalDiscrepancies
) {
}
