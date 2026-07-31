package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.response.PlatformDashboardResponse;
import kh.edu.istad.ite.features.admin.service.AdminDashboardService;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int GROWTH_WINDOW_MONTHS = 6;
    private static final int ACTIVITY_WINDOW_DAYS = 30;
    private static final int ACTIVE_BUSINESS_LIMIT = 5;

    private final BusinessRepository businessRepository;
    private final SaleRepository saleRepository;
    private final BusinessTelegramBotRepository telegramBotRepository;

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardResponse getDashboard() {
        LocalDateTime activitySince = LocalDateTime.now().minusDays(ACTIVITY_WINDOW_DAYS);
        LocalDateTime growthSince = LocalDateTime.now().minusMonths(GROWTH_WINDOW_MONTHS);

        long total = businessRepository.count();
        long newLast30Days = businessRepository.countByCreatedDateGreaterThanEqual(activitySince);
        long active = businessRepository.countByStatus(BusinessOwnerStatus.ACTIVE);
        long suspended = businessRepository.countByStatus(BusinessOwnerStatus.SUSPENDED);
        long deleted = businessRepository.countByStatus(BusinessOwnerStatus.DELETED);
        long closed = businessRepository.countByIsClosedTrue();

        long ordersPaid = saleRepository.countBySoldAtGreaterThanEqual(activitySince);
        long tradingBusinesses = saleRepository.countTradingBusinessesSince(activitySince);
        long storefronts = businessRepository.countByIsListingTrueAndIsClosedFalse();
        long telegramBots = telegramBotRepository.countByIsActiveTrue();

        var byCategory = businessRepository.countGroupedByCategory().stream()
                .map(row -> new PlatformDashboardResponse.CategoryCountResponse(
                        row.getCategoryName(), row.getBusinessCount()))
                .toList();

        var growth = businessRepository.countGroupedByMonth(growthSince).stream()
                .map(row -> new PlatformDashboardResponse.MonthlyCountResponse(
                        row.getMonth(), row.getCount()))
                .toList();

        var orderTrend = saleRepository.countGroupedByMonth(growthSince).stream()
                .map(row -> new PlatformDashboardResponse.MonthlyCountResponse(
                        row.getMonth(), row.getCount()))
                .toList();

        List<PlatformDashboardResponse.ActiveBusinessResponse> mostActive =
                saleRepository.findMostActiveBusinessesSince(activitySince).stream()
                        .limit(ACTIVE_BUSINESS_LIMIT)
                        .map(row -> new PlatformDashboardResponse.ActiveBusinessResponse(
                                row.getBusinessId(), row.getBusinessName(),
                                row.getOrders(), row.getItemsSold()))
                        .toList();

        return new PlatformDashboardResponse(
                total, newLast30Days, active, suspended, deleted, closed,
                ordersPaid, tradingBusinesses, storefronts, telegramBots,
                byCategory, growth, orderTrend, mostActive
        );
    }
}
