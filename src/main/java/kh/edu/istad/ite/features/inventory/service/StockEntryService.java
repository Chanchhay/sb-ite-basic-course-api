package kh.edu.istad.ite.features.inventory.service;

import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.shared.enums.StockEntryType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StockEntryService {

    StockEntryResponse createStockEntry(UUID businessId, CreateStockEntryRequest request);

    List<StockEntryResponse> findAllStockEntries(
            UUID businessId,
            UUID productId,
            StockEntryType entryType,
            String referenceType,
            UUID referenceId,
            LocalDateTime from,
            LocalDateTime to
    );

    StockEntryResponse findStockEntryById(UUID businessId, UUID stockEntryId);

    List<StockEntryResponse> findProductStockEntries(UUID businessId, UUID productId);

    List<StockSummaryResponse> findCurrentStock(UUID businessId);

    StockSummaryResponse findCurrentStockByProduct(UUID businessId, UUID productId);
}
