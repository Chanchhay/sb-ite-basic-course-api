package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.shared.enums.StockEntryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Posts an imported starting quantity into the inventory ledger.
 *
 * Through the ordinary stock service, which opens the costed layer the item
 * will later be sold out of and keeps the running balance straight. Writing a
 * quantity onto the item instead would leave every other part of the
 * application — sales cost, valuation, low-stock alerts, the batch queue —
 * reading a shelf that the ledger says is empty.
 *
 * Each posting is tagged with the import it came from, so a year later the
 * ledger can still answer where an opening balance came from, and so a repeat
 * of the same import can be recognised for what it is.
 */
@Component
@RequiredArgsConstructor
public class OpeningStockPoster {

    /** What the ledger records against stock that arrived by migration. */
    public static final String REFERENCE_TYPE = "DATA_MIGRATION";

    private final StockEntryService stockEntryService;

    /**
     * @param unitCost what one unit cost. The ledger insists on a figure for
     *                 stock arriving — it is what a later sale is costed
     *                 against — so an unpriced import is recorded as zero and
     *                 the row carries a warning saying so, rather than being
     *                 refused outright.
     * @return the entry posted, or null when there was nothing to post
     */
    public UUID post(
            UUID businessId,
            UUID itemId,
            BigDecimal quantity,
            BigDecimal unitCost,
            UUID importJobId,
            String reference
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        CreateStockEntryRequest request = new CreateStockEntryRequest(
                itemId,
                null,
                null,
                StockEntryType.OPENING_STOCK.name(),
                quantity,
                unitCost == null ? BigDecimal.ZERO : unitCost,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                REFERENCE_TYPE,
                importJobId,
                reference,
                "Imported opening stock"
        );

        StockEntryResponse posted = stockEntryService.createStockEntry(businessId, request);

        return posted.id();
    }
}
