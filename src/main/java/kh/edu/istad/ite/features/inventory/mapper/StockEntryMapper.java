package kh.edu.istad.ite.features.inventory.mapper;

import kh.edu.istad.ite.features.inventory.dto.StockConsumptionResponse;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.catalog.mapper.UnitMapper;
import kh.edu.istad.ite.features.inventory.entity.StockConsumption;
import kh.edu.istad.ite.features.inventory.entity.StockLayer;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class StockEntryMapper {

    private final UnitMapper unitMapper;

    public StockEntryMapper(UnitMapper unitMapper) {
        this.unitMapper = unitMapper;
    }

    public StockEntryResponse toResponse(StockEntry stockEntry) {
        return toResponse(stockEntry, List.of());
    }

    /**
     * A movement, with the batches it drew from spelled out.
     *
     * Only the endpoint that returns one movement asks for these. On a list
     * they would be a query per row to answer something no row is showing.
     */
    public StockEntryResponse toResponse(
            StockEntry stockEntry,
            List<StockConsumption> consumptions
    ) {
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
                stockEntry.getLotNumber(),
                stockEntry.getManufacturedAt(),
                stockEntry.getExpiresAt(),
                stockEntry.getBatchData(),
                stockEntry.getReferenceType(),
                stockEntry.getReferenceId(),
                stockEntry.getReferenceNumber(),
                stockEntry.getReason(),
                consumptions.stream().map(this::toConsumption).toList(),
                stockEntry.getCreatedBy(),
                stockEntry.getCreatedDate()
        );
    }

    /**
     * One batch a movement took from, and what that share of it cost.
     *
     * The cost is multiplied out here rather than stored: the pair it comes
     * from is what was recorded, and a total kept beside them is one more
     * thing that can disagree with them.
     */
    private StockConsumptionResponse toConsumption(StockConsumption consumption) {
        StockLayer layer = consumption.getStockLayer();

        return new StockConsumptionResponse(
                layer.getId(),
                layer.getLotNumber(),
                layer.getExpiresAt(),
                layer.getReceivedAt(),
                consumption.getQuantity(),
                consumption.getUnitCost(),
                consumption.getQuantity()
                        .multiply(consumption.getUnitCost())
                        .setScale(2, RoundingMode.HALF_UP)
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
