package kh.edu.istad.ite.features.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SalesPredictionsResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long risingCount,
        long stockoutSoonCount,
        long slowMoverCount,
        RevenueForecast revenueForecast,
        List<PredictionItem> items
) {
    public record RevenueForecast(
            BigDecimal low,
            BigDecimal mid,
            BigDecimal high
    ) {
    }
}
