package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.shared.enums.ItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TelegramStockHelper {

    private final StockEntryService stockEntryService;

    /**
     * What is left of what the customer actually picked.
     *
     * An option is counted on its own, so a basket asks about the option in
     * it. The item's total would say a shop with ten Smalls and no Larges has
     * plenty, and let the Large be sold.
     */
    public Optional<BigDecimal> trackedAvailableQuantity(UUID businessId, Item item, ItemVariant variant) {
        if (item.getItemType() != ItemType.PHYSICAL) {
            return Optional.empty();
        }

        StockSummaryResponse summary = stockEntryService.findAvailableStock(
                businessId, item.getId(), variant == null ? null : variant.getId());
        if (summary.lastEntryId() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(summary.quantityOnHand()).or(() -> Optional.of(BigDecimal.ZERO));
    }

    public Optional<BigDecimal> trackedAvailableQuantity(UUID businessId, Item item) {
        return trackedAvailableQuantity(businessId, item, null);
    }

    /** {@code requestedBaseQuantity} is in base units — packs already unpacked. */
    public boolean hasEnoughStock(
            UUID businessId, Item item, ItemVariant variant, BigDecimal requestedBaseQuantity) {
        return trackedAvailableQuantity(businessId, item, variant)
                .map(available -> available.compareTo(requestedBaseQuantity) >= 0)
                .orElse(true);
    }

    public boolean hasEnoughStock(UUID businessId, Item item, ItemVariant variant, int requestedQuantity) {
        return hasEnoughStock(businessId, item, variant, BigDecimal.valueOf(requestedQuantity));
    }

    public boolean hasEnoughStock(UUID businessId, Item item, int requestedQuantity) {
        return hasEnoughStock(businessId, item, null, requestedQuantity);
    }
}
