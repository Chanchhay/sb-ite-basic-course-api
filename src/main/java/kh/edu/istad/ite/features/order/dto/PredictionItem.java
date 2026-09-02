package kh.edu.istad.ite.features.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PredictionItem(
        UUID itemId,
        String name,
        BigDecimal currentStock,
        Double avgDailyDemand,
        Long expectedDemandWindow,
        Double trendPercent,
        Double estimatedStockoutDays,
        Long recommendedRestock,
        Long qtySold30d
) {
}
