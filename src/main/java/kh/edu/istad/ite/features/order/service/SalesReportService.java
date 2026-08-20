package kh.edu.istad.ite.features.order.service;

import kh.edu.istad.ite.features.order.dto.CollectPayLaterRequest;
import kh.edu.istad.ite.features.order.dto.DailyChannelRevenue;
import kh.edu.istad.ite.features.order.dto.SaleResponse;
import kh.edu.istad.ite.features.order.dto.SalesProfitResponse;
import kh.edu.istad.ite.features.order.entity.Sale;
import kh.edu.istad.ite.features.order.mapper.OrderMapper;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private static final String CURRENCY_KHR = "KHR";

    private final SaleRepository saleRepository;
    private final BusinessHelper businessHelper;
    private final OrderMapper orderMapper;

    public SalesProfitResponse profitByChannel(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to) {

        List<SalesProfitResponse.ChannelProfit> channels = new ArrayList<>();

        long sales = 0;
        long itemsSold = 0;
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        for (SaleRepository.ChannelProfitProjection row
                : saleRepository.profitByChannel(businessId, from, to)) {

            channels.add(toChannelProfit(
                    row.getChannel(),
                    row.getSales(),
                    row.getItemsSold(),
                    row.getGrossSales(),
                    row.getDiscounts(),
                    row.getRevenue(),
                    row.getCost()));

            // Totalled from the same rows rather than by a second query, so
            // the total can never disagree with the lines above it.
            sales += row.getSales();
            itemsSold += row.getItemsSold();
            grossSales = grossSales.add(orZero(row.getGrossSales()));
            discounts = discounts.add(orZero(row.getDiscounts()));
            revenue = revenue.add(orZero(row.getRevenue()));
            cost = cost.add(orZero(row.getCost()));
        }

        return new SalesProfitResponse(
                channels,
                toChannelProfit(null, sales, itemsSold, grossSales, discounts, revenue, cost));
    }

    private static SalesProfitResponse.ChannelProfit toChannelProfit(
            kh.edu.istad.ite.shared.enums.OrderChannel channel,
            long sales,
            long itemsSold,
            BigDecimal grossSales,
            BigDecimal discounts,
            BigDecimal revenue,
            BigDecimal cost) {

        BigDecimal takings = orZero(revenue);
        BigDecimal spent = orZero(cost);
        BigDecimal profit = takings.subtract(spent);

        return new SalesProfitResponse.ChannelProfit(
                channel,
                sales,
                itemsSold,
                money(grossSales),
                money(discounts),
                money(takings),
                money(spent),
                money(profit),
                // Nothing taken is no margin, not a margin of nothing.
                takings.signum() == 0
                        ? null
                        : profit.multiply(BigDecimal.valueOf(100))
                                .divide(takings, 2, RoundingMode.HALF_UP));
    }

    public List<DailyChannelRevenue> dailyRevenueByChannel(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to) {

        return saleRepository.dailyRevenueByChannel(businessId, from, to).stream()
                .map(row -> new DailyChannelRevenue(
                        LocalDate.parse(row.getDay()),
                        OrderChannel.valueOf(row.getChannel()),
                        money(row.getRevenue())))
                .toList();
    }

    /** Every sale rung up as "pay later" that hasn't been collected yet. */
    public List<SaleResponse> payLaterSales(UUID businessId) {
        businessHelper.findAccessibleBusiness(businessId);

        return saleRepository.findUnsettledByBusinessId(businessId).stream()
                .map(orderMapper::toSaleResponse)
                .toList();
    }

    /** Settles a pay-later sale once the money actually comes in. */
    @Transactional
    public SaleResponse collectPayLater(UUID businessId, UUID saleId, CollectPayLaterRequest request) {
        businessHelper.findAccessibleBusiness(businessId);

        Sale sale = saleRepository.findById(saleId)
                .filter(s -> s.getBusiness().getId().equals(businessId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale has not been found"));

        if (sale.getPaidAmount().compareTo(sale.getTotalAmount()) >= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This sale has already been settled");
        }

        if (kh.edu.istad.ite.shared.enums.PaymentMethodType.PAY_LATER.equals(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Collecting a sale requires an actual payment method");
        }

        int scale = CURRENCY_KHR.equalsIgnoreCase(sale.getCurrency()) ? 0 : 2;
        BigDecimal owed = sale.getTotalAmount().subtract(sale.getPaidAmount());
        BigDecimal received = request.receivedAmount() == null
                ? owed
                : request.receivedAmount().setScale(scale, RoundingMode.HALF_UP);

        if (received.compareTo(owed) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Received " + received + " is less than the amount owed " + owed);
        }

        sale.setPaidAmount(sale.getTotalAmount());
        sale.setChangeAmount(received.subtract(owed).setScale(scale, RoundingMode.HALF_UP));
        sale.setPaymentMethod(request.paymentMethod());

        return orderMapper.toSaleResponse(saleRepository.save(sale));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return orZero(value).setScale(2, RoundingMode.HALF_UP);
    }
}
