package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.shared.enums.ItemType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TelegramStockHelper {

    private final StockEntryService stockEntryService;

    private final ItemChannelStockService itemChannelStockService;

    /**
     * What is left of what the customer actually picked, on the channel they
     * picked it from.
     *
     * An option is counted on its own, so a basket asks about the option in
     * it. The item's total would say a shop with ten Smalls and no Larges has
     * plenty, and let the Large be sold.
     *
     * The channel narrows it again: a shop that has given Telegram three of
     * its twelve has said the other nine are somebody else's, so three is what
     * the bot may offer.
     */
    public Optional<BigDecimal> trackedAvailableQuantity(
            UUID businessId, Item item, ItemVariant variant, OrderChannel channel) {
        if (item.getItemType() != ItemType.PHYSICAL) {
            return Optional.empty();
        }

        StockSummaryResponse summary = stockEntryService.findAvailableStock(
                businessId, item.getId(), variant == null ? null : variant.getId());
        if (summary.lastEntryId() == null) {
            return Optional.empty();
        }

        BigDecimal onHand = summary.quantityOnHand() == null
                ? BigDecimal.ZERO
                : summary.quantityOnHand();

        return Optional.of(itemChannelStockService.availableFor(item, variant, channel, onHand));
    }

    public Optional<BigDecimal> trackedAvailableQuantity(
            UUID businessId, Item item, OrderChannel channel) {
        return trackedAvailableQuantity(businessId, item, null, channel);
    }

    /** {@code requestedBaseQuantity} is in base units — packs already unpacked. */
    public boolean hasEnoughStock(
            UUID businessId, Item item, ItemVariant variant,
            BigDecimal requestedBaseQuantity, OrderChannel channel) {
        return trackedAvailableQuantity(businessId, item, variant, channel)
                .map(available -> available.compareTo(requestedBaseQuantity) >= 0)
                .orElse(true);
    }

    public boolean hasEnoughStock(
            UUID businessId, Item item, ItemVariant variant,
            int requestedQuantity, OrderChannel channel) {
        return hasEnoughStock(
                businessId, item, variant, BigDecimal.valueOf(requestedQuantity), channel);
    }
}
