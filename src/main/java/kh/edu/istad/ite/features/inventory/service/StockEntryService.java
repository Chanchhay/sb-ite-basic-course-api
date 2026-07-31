package kh.edu.istad.ite.features.inventory.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import kh.edu.istad.ite.shared.enums.StockEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StockEntryService {

    StockEntryResponse createStockEntry(UUID businessId, CreateStockEntryRequest request);

    StockEntry recordSale(Business business, Item item, BigDecimal quantity, UUID orderId, String invoiceNumber);

    BigDecimal findLatestUnitCost(UUID businessId, UUID itemId);

    List<StockEntryResponse> findAllStockEntries(
            UUID businessId,
            UUID itemId,
            StockEntryType entryType,
            String referenceType,
            UUID referenceId,
            LocalDateTime from,
            LocalDateTime to
    );

    StockEntryResponse findStockEntryById(UUID businessId, UUID stockEntryId);

    List<StockEntryResponse> findItemStockEntries(UUID businessId, UUID itemId);

    List<StockSummaryResponse> findCurrentStock(UUID businessId);

    StockSummaryResponse findCurrentStockByItem(UUID businessId, UUID itemId);

    StockSummaryResponse findAvailableStock(UUID businessId, UUID itemId);
}
