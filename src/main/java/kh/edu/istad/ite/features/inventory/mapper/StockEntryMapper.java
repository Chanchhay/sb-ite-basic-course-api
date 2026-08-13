package kh.edu.istad.ite.features.inventory.mapper;

import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.catalog.mapper.UnitMapper;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class StockEntryMapper {

    private final UnitMapper unitMapper;

    public StockEntryMapper(UnitMapper unitMapper) {
        this.unitMapper = unitMapper;
    }

    public StockEntryResponse toResponse(StockEntry stockEntry) {
        return new StockEntryResponse(
                stockEntry.getId(),
                stockEntry.getBusiness().getId(),
                stockEntry.getItem() == null ? null : stockEntry.getItem().getId(),
                stockEntry.getAddOn() == null ? null : stockEntry.getAddOn().getId(),
                stockEntry.getVariant() == null ? null : stockEntry.getVariant().getId(),
                stockEntry.getVariant() == null ? null : stockEntry.getVariant().getVariantName(),
                stockEntry.getEntryType(),
                stockEntry.getQuantityChange(),
                stockEntry.getQuantityBefore(),
                stockEntry.getQuantityAfter(),
                stockEntry.getUnitCost(),
                stockEntry.getCostOfGoods(),
                stockEntry.getUnitSalePrice(),
                stockEntry.getEnteredQuantity(),
                stockEntry.getEnteredUnit() == null
                        ? null
                        : unitMapper.toResponse(stockEntry.getEnteredUnit()),
                stockEntry.getBatchData(),
                stockEntry.getReferenceType(),
                stockEntry.getReferenceId(),
                stockEntry.getReferenceNumber(),
                stockEntry.getReason(),
                stockEntry.getCreatedBy(),
                stockEntry.getCreatedDate()
        );
    }

    /**
     * The balance this entry left behind, priced from the batches still open.
     *
     * The quantity comes off the entry; what it is worth cannot — an entry
     * knows what *it* cost, not what is left on the shelf.
     */
    public StockSummaryResponse toSummary(
            StockEntry stockEntry,
            BigDecimal stockValue,
            BigDecimal unitCost
    ) {
        return new StockSummaryResponse(
                stockEntry.getItem() == null ? null : stockEntry.getItem().getId(),
                stockEntry.getAddOn() == null ? null : stockEntry.getAddOn().getId(),
                stockEntry.getVariant() == null ? null : stockEntry.getVariant().getId(),
                stockEntry.getVariant() == null ? null : stockEntry.getVariant().getVariantName(),
                stockEntry.getQuantityAfter(),
                stockValue,
                unitCost,
                stockEntry.getId(),
                stockEntry.getCreatedDate()
        );
    }

    public StockSummaryResponse toSummary(StockEntry stockEntry) {
        return toSummary(stockEntry, null, null);
    }

    /**
     * What an item holds in total, across every option it is counted in.
     *
     * The quantity is a sum rather than any one entry's, but the last entry is
     * still named: callers read a null there as "this shop does not track this
     * item at all" and would otherwise sell it without limit.
     */
    public StockSummaryResponse itemTotal(
            UUID itemId,
            BigDecimal quantityOnHand,
            BigDecimal stockValue,
            BigDecimal unitCost,
            UUID lastEntryId,
            LocalDateTime updatedAt
    ) {
        return new StockSummaryResponse(
                itemId, null, null, null, quantityOnHand, stockValue, unitCost, lastEntryId, updatedAt);
    }

    public StockSummaryResponse emptySummary(UUID itemId) {
        return new StockSummaryResponse(
                itemId, null, null, null, BigDecimal.ZERO.setScale(3), null, null, null, null);
    }

    /**
     * An option no movement has ever named, on an item that is tracked.
     *
     * It holds nothing, and says so — carrying the item's last entry id, since
     * a null there means "this shop does not track the item" and would let the
     * option sell without limit.
     */
    public StockSummaryResponse emptyVariantSummary(UUID itemId, UUID variantId, UUID lastEntryId) {
        return new StockSummaryResponse(
                itemId, null, variantId, null, BigDecimal.ZERO.setScale(3), null, null, lastEntryId, null);
    }

    public StockSummaryResponse emptyAddOnSummary(UUID addOnId) {
        return new StockSummaryResponse(
                null, addOnId, null, null, BigDecimal.ZERO.setScale(3), null, null, null, null);
    }
}
