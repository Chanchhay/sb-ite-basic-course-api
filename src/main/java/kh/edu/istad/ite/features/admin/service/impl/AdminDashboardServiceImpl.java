package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.response.PlatformDashboardResponse;
import kh.edu.istad.ite.features.admin.service.AdminDashboardService;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int GROWTH_WINDOW_MONTHS = 6;

    private final BusinessRepository businessRepository;

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardResponse getDashboard() {
        long total = businessRepository.count();
        long newLast30Days = businessRepository.countByCreatedDateGreaterThanEqual(LocalDateTime.now().minusDays(30));
        long active = businessRepository.countByStatus(BusinessOwnerStatus.ACTIVE);
        long suspended = businessRepository.countByStatus(BusinessOwnerStatus.SUSPENDED);
        long deleted = businessRepository.countByStatus(BusinessOwnerStatus.DELETED);
        long closed = businessRepository.countByIsClosedTrue();

        var byCategory = businessRepository.countGroupedByCategory().stream()
                .map(row -> new PlatformDashboardResponse.CategoryCountResponse(row.getCategoryName(), row.getBusinessCount()))
                .toList();

        var growth = businessRepository.countGroupedByMonth(LocalDateTime.now().minusMonths(GROWTH_WINDOW_MONTHS)).stream()
                .map(row -> new PlatformDashboardResponse.MonthlyCountResponse(row.getMonth(), row.getCount()))
                .toList();

        return new PlatformDashboardResponse(total, newLast30Days, active, suspended, deleted, closed, byCategory, growth);
    }
}
