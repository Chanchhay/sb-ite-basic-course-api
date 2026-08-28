package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.canonical.OpeningStockImportRecord;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.inventory.repository.StockEntryRepository;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OpeningStockImportCommitter implements ImportCommitter {

    private final ItemRepository itemRepository;
    private final StockEntryRepository stockEntryRepository;
    private final OpeningStockPoster openingStockPoster;

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.OPENING_STOCK;
    }

    @Override
    public CommitOutcome commit(
            ImportJob job,
            ImportRecord record,
            UUID matchedEntityId,
            MappingPlan plan
    ) {
        OpeningStockImportRecord stock = (OpeningStockImportRecord) record;
        UUID businessId = job.getBusiness().getId();

        /*
         * A duplicate here is an item that already has a stock history, and
         * there is nothing safe to do with it. The ledger allows one opening
         * balance and no more, and adding the quantity again would put stock
         * on the shelf that nobody delivered. Opening stock therefore has no
         * update strategy — it skips, whatever the import was set to.
         */
        if (matchedEntityId != null) {
            return CommitOutcome.skipped(matchedEntityId);
        }

        Item item = findItem(businessId, stock);

        if (item == null) {
            return CommitOutcome.failed(
                    "No item found for \"" + stock.externalId() + "\"."
            );
        }

        if (stock.quantity() == null || stock.quantity().compareTo(BigDecimal.ZERO) == 0) {
            /*
             * A counted zero is a real answer — the shelf is empty — but there
             * is no movement to record, and the ledger refuses an entry that
             * moves nothing. The item simply starts at nothing, which it does
             * anyway.
             */
            return CommitOutcome.skipped(item.getId());
        }

        if (stockEntryRepository
                .findFirstByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(businessId, item.getId())
                .isPresent()) {
            return CommitOutcome.skipped(item.getId());
        }

        UUID stockEntryId = openingStockPoster.post(
                businessId,
                item.getId(),
                stock.quantity(),
                stock.unitCost(),
                job.getId(),
                job.getSourceFileName()
        );

        return CommitOutcome.created(item.getId(), stockEntryId, List.of());
    }

    /**
     * The item this row is about, by SKU, then barcode, then name.
     *
     * The same order checking used, so the row commits against the item the
     * shop was shown in the preview.
     */
    private Item findItem(UUID businessId, OpeningStockImportRecord stock) {
        if (stock.sku() != null) {
            Item bySku = first(itemRepository.findByBusinessIdAndSkuIgnoreCase(businessId, stock.sku()));
            if (bySku != null) {
                return bySku;
            }
        }

        if (stock.barcode() != null) {
            Item byBarcode = itemRepository
                    .findByBusinessIdAndBarcode(businessId, stock.barcode())
                    .orElse(null);
            if (byBarcode != null) {
                return byBarcode;
            }
        }

        return stock.itemName() == null
                ? null
                : first(itemRepository.findByBusinessIdAndNameIgnoreCase(businessId, stock.itemName()));
    }

    private Item first(List<Item> items) {
        return items.isEmpty() ? null : items.getFirst();
    }
}
