package kh.edu.istad.ite.features.admin.dto.response;

import java.util.List;
import java.util.UUID;

public record PlatformDashboardResponse(
        long totalBusinesses,
        long newBusinessesLast30Days,
        long activeBusinesses,
        long suspendedBusinesses,
        long deletedBusinesses,
        long closedBusinesses,

        long ordersPaidLast30Days,
        long tradingBusinessesLast30Days,
        long storefrontsPublished,
        long telegramBotsConnected,

        List<CategoryCountResponse> businessesByCategory,
        List<MonthlyCountResponse> businessGrowth,
        List<MonthlyCountResponse> orderTrend,
        List<ActiveBusinessResponse> mostActiveBusinesses
) {

    public record CategoryCountResponse(String categoryName, long businessCount) {
    }

    public record MonthlyCountResponse(String month, long count) {
    }

    public record ActiveBusinessResponse(
            UUID businessId,
            String businessName,
            long orders,
            long itemsSold
    ) {
    }
}
