package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
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

    public Optional<BigDecimal> trackedAvailableQuantity(UUID businessId, Item item) {
        if (item.getItemType() != ItemType.PHYSICAL) {
            return Optional.empty();
        }

        StockSummaryResponse summary = stockEntryService.findAvailableStock(businessId, item.getId());
        if (summary.lastEntryId() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(summary.quantityOnHand()).or(() -> Optional.of(BigDecimal.ZERO));
    }

    public boolean hasEnoughStock(UUID businessId, Item item, int requestedQuantity) {
        return trackedAvailableQuantity(businessId, item)
                .map(available -> available.compareTo(BigDecimal.valueOf(requestedQuantity)) >= 0)
                .orElse(true);
    }
}
