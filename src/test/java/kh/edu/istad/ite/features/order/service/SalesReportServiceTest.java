package kh.edu.istad.ite.features.order.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.dto.PredictionWindow;
import kh.edu.istad.ite.features.order.dto.SalesPredictionsResponse;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private StockEntryService stockEntryService;
    @Mock
    private BusinessHelper businessHelper;

    @InjectMocks
    private SalesReportService salesReportService;

    @Test
    void getSalesPredictions_weekWindow_returnsWindowDays7() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        Item item = new Item();
        item.setId(itemId);
        item.setName("Test Beverage 330ml");

        when(itemRepository.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(item));
        when(stockEntryService.findCurrentStock(businessId)).thenReturn(Collections.emptyList());
        when(saleRepository.findItemPredictionBuckets(eq(businessId), any(LocalDateTime.class), eq(7), eq(14), eq(30)))
                .thenReturn(Collections.emptyList());
        when(saleRepository.findBusinessRevenueTrend(eq(businessId), any(LocalDateTime.class), eq(7), eq(14)))
                .thenReturn(null);

        SalesPredictionsResponse response = salesReportService.getSalesPredictions(businessId, PredictionWindow.WEEK);

        assertThat(response).isNotNull();
        assertThat(response.windowDays()).isEqualTo(7);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("Test Beverage 330ml");
    }

    @Test
    void getSalesPredictions_monthWindow_returnsWindowDays30() {
        UUID businessId = UUID.randomUUID();

        when(itemRepository.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(Collections.emptyList());
        when(stockEntryService.findCurrentStock(businessId)).thenReturn(Collections.emptyList());
        when(saleRepository.findItemPredictionBuckets(eq(businessId), any(LocalDateTime.class), eq(30), eq(60), eq(60)))
                .thenReturn(Collections.emptyList());
        when(saleRepository.findBusinessRevenueTrend(eq(businessId), any(LocalDateTime.class), eq(30), eq(60)))
                .thenReturn(null);

        SalesPredictionsResponse response = salesReportService.getSalesPredictions(businessId, PredictionWindow.MONTH);

        assertThat(response).isNotNull();
        assertThat(response.windowDays()).isEqualTo(30);
    }
}
