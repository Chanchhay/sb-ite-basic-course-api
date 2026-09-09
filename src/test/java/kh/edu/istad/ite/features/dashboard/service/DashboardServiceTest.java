package kh.edu.istad.ite.features.dashboard.service;

import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.dashboard.dto.DashboardOverviewResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.dto.ItemProfitResponse;
import kh.edu.istad.ite.features.order.dto.PeriodProfitResponse;
import kh.edu.istad.ite.features.order.dto.SalesProfitResponse;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.order.service.SalesReportService;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.ReportGranularity;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SalesReportService salesReportService;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StockEntryService stockEntryService;
    @Mock
    private BusinessHelper businessHelper;

    @InjectMocks
    private DashboardService dashboardService;

    private UUID businessId;
    private LocalDateTime from;
    private LocalDateTime to;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        from = LocalDateTime.of(2026, 1, 1, 0, 0);
        to = LocalDateTime.of(2026, 1, 31, 23, 59);
    }

    @Test
    @DisplayName("Channel shares percentage should sum to exactly 100% with skewed revenues")
    void overview_channelSharesSumTo100() {
        // POS: 996 (99.6%), WEB: 1 (0.1%), MESSENGER: 1 (0.1%), TELEGRAM: 2 (0.2%)
        SalesProfitResponse.ChannelProfit pos = createChannelProfit(OrderChannel.POS, new BigDecimal("996"));
        SalesProfitResponse.ChannelProfit web = createChannelProfit(OrderChannel.WEB, new BigDecimal("1"));
        SalesProfitResponse.ChannelProfit msg = createChannelProfit(OrderChannel.MESSENGER, new BigDecimal("1"));
        SalesProfitResponse.ChannelProfit tg = createChannelProfit(OrderChannel.TELEGRAM, new BigDecimal("2"));

        SalesProfitResponse report = new SalesProfitResponse(
                List.of(pos, web, msg, tg),
                createChannelProfit(null, new BigDecimal("1000"))
        );

        when(salesReportService.profitByChannel(eq(businessId), any(), any()))
                .thenReturn(report);
        when(itemRepository.findAllByBusinessIdOrderByNameAsc(businessId))
                .thenReturn(List.of());
        when(stockEntryService.findCurrentStock(businessId))
                .thenReturn(List.of());
        when(salesReportService.profitByPeriod(eq(businessId), any(), any(), any()))
                .thenReturn(new PeriodProfitResponse(ReportGranularity.DAY, List.of(), null));
        when(salesReportService.profitByItem(eq(businessId), any(), any()))
                .thenReturn(new ItemProfitResponse(List.of(), null));

        DashboardOverviewResponse response = dashboardService.overview(
                businessId, from, to, ReportGranularity.DAY);

        List<DashboardOverviewResponse.ChannelShare> channels = response.channels();
        assertEquals(4, channels.size());

        int totalPercentage = channels.stream()
                .mapToInt(DashboardOverviewResponse.ChannelShare::percentage)
                .sum();

        assertEquals(100, totalPercentage, "Total channel share percentage must sum to exactly 100%");

        // POS has largest remainder after floor 99, so it gets 100%, others get 0%
        DashboardOverviewResponse.ChannelShare posShare = channels.stream()
                .filter(c -> "POS".equals(c.channel()))
                .findFirst().orElseThrow();
        assertEquals(100, posShare.percentage());
    }

    private SalesProfitResponse.ChannelProfit createChannelProfit(OrderChannel channel, BigDecimal revenue) {
        return new SalesProfitResponse.ChannelProfit(
                channel,
                1L,
                1L,
                revenue,
                BigDecimal.ZERO,
                revenue,
                BigDecimal.ZERO,
                revenue,
                BigDecimal.valueOf(100)
        );
    }
}
