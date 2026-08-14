package kh.edu.istad.ite.features.order;

import kh.edu.istad.ite.features.order.dto.SalesProfitResponse;
import kh.edu.istad.ite.features.order.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/** What the shop made, read back after the fact. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/sales")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;

    /**
     * Revenue, cost and profit for every channel the shop sells on.
     *
     * Both ends of the range are optional, and leaving them out means all
     * time — a shop asking "how are we doing" before it has decided on a
     * period should get an answer rather than a validation error.
     */
    @GetMapping("/profit")
    public SalesProfitResponse profitByChannel(
            @PathVariable UUID businessId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return salesReportService.profitByChannel(businessId, from, to);
    }
}
