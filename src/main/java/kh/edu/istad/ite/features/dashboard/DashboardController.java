package kh.edu.istad.ite.features.dashboard;

import kh.edu.istad.ite.features.dashboard.dto.BestSellingRow;
import kh.edu.istad.ite.features.dashboard.dto.DashboardOverviewResponse;
import kh.edu.istad.ite.features.dashboard.dto.RecentOrderRow;
import kh.edu.istad.ite.features.dashboard.service.DashboardService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.ReportGranularity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The dashboard, answered rather than assembled.
 *
 * Each endpoint here returns what a part of the screen draws, finished. The
 * screen chooses colours and formats currency; it does not sum, rank,
 * accumulate, join or paginate.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Every card on the dashboard at once.
     *
     * Both ends of the range are optional, and leaving them out means all
     * time — the same contract the sales reports keep, so a shop asking "how
     * are we doing" before choosing a period gets an answer rather than a
     * validation error.
     */
    @GetMapping("/overview")
    public DashboardOverviewResponse overview(
            @PathVariable UUID businessId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity) {

        return dashboardService.overview(businessId, from, to, granularity);
    }

    /**
     * The recent orders table.
     *
     * `search` is applied before paging, so it finds rows that are not on the
     * current page — which is the only reason to search at all.
     */
    @GetMapping("/recent-orders")
    public PageResponse<RecentOrderRow> recentOrders(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 5) Pageable pageable) {

        return dashboardService.recentOrders(businessId, search, pageable);
    }

    /**
     * The catalogue ranked by what it sold.
     *
     * Ranked over every item before the page is cut, so page two is genuinely
     * the next five best sellers rather than five rows sorted among themselves.
     */
    @GetMapping("/best-selling")
    public PageResponse<BestSellingRow> bestSelling(
            @PathVariable UUID businessId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 5) Pageable pageable) {

        return dashboardService.bestSelling(businessId, from, to, search, pageable);
    }
}
