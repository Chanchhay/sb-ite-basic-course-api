package kh.edu.istad.ite.features.admin.dto.response;

import java.util.List;

public record PlatformDashboardResponse(
        long totalBusinesses,
        long newBusinessesLast30Days,
        long activeBusinesses,
        long suspendedBusinesses,
        long deletedBusinesses,
        long closedBusinesses,
        List<CategoryCountResponse> businessesByCategory,
        List<MonthlyCountResponse> businessGrowth
) {
    public record CategoryCountResponse(String categoryName, long businessCount) {
    }

    public record MonthlyCountResponse(String month, long count) {
    }
}
