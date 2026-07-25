package kh.edu.istad.ite.features.inventory;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.shared.enums.StockEntryType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
@RequiredArgsConstructor
public class StockEntryController {

    private final StockEntryService stockEntryService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/stock-entries")
    public StockEntryResponse createStockEntry(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateStockEntryRequest request
    ) {
        return stockEntryService.createStockEntry(businessId, request);
    }

    @GetMapping("/stock-entries")
    public List<StockEntryResponse> findAllStockEntries(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockEntryType entryType,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) UUID referenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return stockEntryService.findAllStockEntries(
                businessId,
                productId,
                entryType,
                referenceType,
                referenceId,
                from,
                to
        );
    }

    @GetMapping("/stock-entries/current")
    public List<StockSummaryResponse> findCurrentStock(@PathVariable UUID businessId) {
        return stockEntryService.findCurrentStock(businessId);
    }

    @GetMapping("/stock-entries/{stockEntryId}")
    public StockEntryResponse findStockEntryById(
            @PathVariable UUID businessId,
            @PathVariable UUID stockEntryId
    ) {
        return stockEntryService.findStockEntryById(businessId, stockEntryId);
    }

    @GetMapping("/items/{productId}/stock-entries")
    public List<StockEntryResponse> findProductStockEntries(
            @PathVariable UUID businessId,
            @PathVariable UUID productId
    ) {
        return stockEntryService.findProductStockEntries(businessId, productId);
    }

    @GetMapping("/items/{productId}/stock")
    public StockSummaryResponse findCurrentStockByProduct(
            @PathVariable UUID businessId,
            @PathVariable UUID productId
    ) {
        return stockEntryService.findCurrentStockByProduct(businessId, productId);
    }
}
