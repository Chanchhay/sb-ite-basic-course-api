package kh.edu.istad.ite.features.inventory.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockBatchResponse;
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

    StockEntry recordSale(Business business, Item item, ItemVariant variant, BigDecimal quantity, UUID orderId, String invoiceNumber);

    /**
     * The same, for a line sold by the pack: {@code quantity} is what leaves
     * the shelf in base units, {@code soldQuantity} and {@code soldUnit} are
     * what the customer bought.
     */
    StockEntry recordSale(Business business, Item item, ItemVariant variant, BigDecimal quantity,
                          BigDecimal soldQuantity, Unit soldUnit, UUID orderId, String invoiceNumber);

    /**
     * An add-on going out with a sale: pearls scooped into the drinks on one
     * line. {@code quantity} is in the add-on's own base units.
     */
    StockEntry recordAddOnSale(Business business, AddOn addOn, BigDecimal quantity,
                               UUID orderId, String invoiceNumber);

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

    /** The same, narrowed to one option. A null option reads the item's total. */
    StockSummaryResponse findAvailableStock(UUID businessId, UUID itemId, UUID variantId);

    /**
     * The deliveries still on the shelf for one item, oldest first.
     *
     * The order they will be sold in, which is the order they are listed in.
     */
    List<StockBatchResponse> findItemBatches(UUID businessId, UUID itemId);
}
