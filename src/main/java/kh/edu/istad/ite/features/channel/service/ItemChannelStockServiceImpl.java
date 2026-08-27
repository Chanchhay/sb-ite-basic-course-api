package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.channel.dto.ChannelStockAllocationRequest;
import kh.edu.istad.ite.features.channel.dto.ChannelStockAvailabilityResponse;
import kh.edu.istad.ite.features.channel.dto.ChannelStockAllocationResponse;
import kh.edu.istad.ite.features.channel.dto.ItemChannelStockResponse;
import kh.edu.istad.ite.features.channel.dto.SaveItemChannelStockRequest;
import kh.edu.istad.ite.features.channel.entity.ItemChannelStock;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;
import kh.edu.istad.ite.features.channel.repository.ItemChannelStockRepository;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.shared.enums.ChannelStockMode;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemChannelStockServiceImpl implements ItemChannelStockService {

    private final ItemChannelStockRepository itemChannelStockRepository;

    private final ItemChannelRepository itemChannelRepository;

    private final ItemRepository itemRepository;

    private final SalesChannelRepository salesChannelRepository;

    private final StockEntryService stockEntryService;

    @Override
    @Transactional(readOnly = true)
    public ItemChannelStockResponse findSplit(UUID businessId, UUID itemId) {
        Item item = requireItem(businessId, itemId);
        List<ItemChannelStock> allocations = itemChannelStockRepository.findByItemId(itemId);

        return new ItemChannelStockResponse(
                itemId,
                modeOf(item),
                allocations.stream().map(this::toResponse).toList(),
                item.getLastModifiedDate());
    }

    @Override
    public ItemChannelStockResponse saveSplit(
            UUID businessId, UUID itemId, SaveItemChannelStockRequest request) {
        Item item = requireItem(businessId, itemId);
        List<ChannelStockAllocationRequest> requested =
                request.allocations() == null ? List.of() : request.allocations();

        // The options this item actually has, so a share cannot be set against
        // one that belongs to another item — a balance nobody could ever sell.
        Map<UUID, ItemVariant> variants = new HashMap<>();
        item.getVariants().forEach(variant -> variants.put(variant.getId(), variant));

        Map<String, ChannelStockAllocationRequest> byLine = new LinkedHashMap<>();
        Map<UUID, BigDecimal> demandByVariant = new LinkedHashMap<>();

        for (ChannelStockAllocationRequest line : requested) {
            if (line.variantId() != null && !variants.containsKey(line.variantId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "That option does not belong to this item.");
            }

            // A channel that does not sell the item cannot hold stock back
            // from the ones that do.
            if (!itemChannelRepository.existsByItemIdAndSalesChannelId(itemId, line.salesChannelId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Publish the item on that channel before allocating stock to it.");
            }

            String key = lineKey(line.salesChannelId(), line.variantId());

            if (byLine.putIfAbsent(key, line) != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "That channel was given two allocations for the same option.");
            }

            demandByVariant.merge(
                    line.variantId() == null ? nullVariantKey() : line.variantId(),
                    line.quantity(),
                    BigDecimal::add);
        }

        // More given out than there is on the shelf is the one way this can be
        // wrong: two channels each promised eight of the ten in the fridge is
        // a shop that has sold six cakes it does not have, and it finds out at
        // the counter. Checked per option, because that is how stock is held.
        if (ChannelStockMode.ALLOCATED.equals(request.mode())) {
            demandByVariant.forEach((variantKey, demanded) -> {
                UUID variantId = nullVariantKey().equals(variantKey) ? null : variantKey;
                StockSummaryResponse summary =
                        stockEntryService.findAvailableStock(businessId, itemId, variantId);

                // Nothing has ever moved: the shop is not counting this item,
                // so there is no figure to be over.
                if (summary.lastEntryId() == null) {
                    return;
                }

                BigDecimal onHand = summary.quantityOnHand() == null
                        ? BigDecimal.ZERO
                        : summary.quantityOnHand();

                if (demanded.compareTo(onHand) > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "You have allocated " + demanded.stripTrailingZeros().toPlainString()
                                    + " but only " + onHand.stripTrailingZeros().toPlainString()
                                    + " are on hand.");
                }
            });
        }

        List<ItemChannelStock> existing = itemChannelStockRepository.findByItemId(itemId);
        Map<String, ItemChannelStock> existingByLine = new LinkedHashMap<>();

        existing.forEach(allocation -> existingByLine.put(
                lineKey(
                        allocation.getSalesChannel().getId(),
                        allocation.getVariant() == null ? null : allocation.getVariant().getId()),
                allocation));

        List<ItemChannelStock> toSave = new ArrayList<>();

        byLine.forEach((key, line) -> {
            ItemChannelStock allocation = existingByLine.remove(key);

            if (allocation == null) {
                SalesChannel channel = salesChannelRepository.findById(line.salesChannelId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Sales channel not found"));

                allocation = new ItemChannelStock();
                allocation.setItem(item);
                allocation.setSalesChannel(channel);
                allocation.setVariant(line.variantId() == null ? null : variants.get(line.variantId()));
            }

            // What has already sold on the channel is left alone: the shop is
            // setting how many it may sell, not unselling what it sold.
            allocation.setQuantity(line.quantity());
            toSave.add(allocation);
        });

        itemChannelStockRepository.saveAll(toSave);
        // Whatever the shop did not send has no allocation any more. Deleting
        // it rather than zeroing it is what makes "shared again" possible: a
        // row at zero would read as a channel forbidden to sell.
        itemChannelStockRepository.deleteAll(existingByLine.values());

        item.setChannelStockMode(request.mode());
        itemRepository.save(item);

        return findSplit(businessId, itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelStockAvailabilityResponse> findChannelAvailability(
            UUID businessId, String channelCode) {
        return itemChannelStockRepository
                .findByChannelCodeAndBusinessId(channelCode, businessId)
                .stream()
                .map(allocation -> new ChannelStockAvailabilityResponse(
                        allocation.getItem().getId(),
                        allocation.getVariant() == null
                                ? null
                                : allocation.getVariant().getId(),
                        allocation.remaining()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal availableFor(
            Item item, ItemVariant variant, OrderChannel channel, BigDecimal onHand) {
        if (item != null && !item.isStockTracked()) {
            return BigDecimal.valueOf(999999999);
        }

        BigDecimal shelf = onHand == null ? BigDecimal.ZERO : onHand;

        if (!ChannelStockMode.ALLOCATED.equals(modeOf(item)) || channel == null) {
            return shelf;
        }

        return findAllocation(item, variant, channel)
                .map(allocation -> allocation.remaining().min(shelf))
                // Allocated, but nothing set aside for this channel: it sells
                // none. Under this mode an absent share is a real answer.
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAllocation(
            Item item, ItemVariant variant, OrderChannel channel, BigDecimal baseQuantity) {
        if (item != null && !item.isStockTracked()) {
            return;
        }

        if (!ChannelStockMode.ALLOCATED.equals(modeOf(item))
                || channel == null
                || baseQuantity == null
                || baseQuantity.signum() <= 0) {
            return;
        }

        BigDecimal remaining = findAllocation(item, variant, channel)
                .map(ItemChannelStock::remaining)
                .orElse(BigDecimal.ZERO);

        if (remaining.compareTo(baseQuantity) < 0) {
            String name = variant == null
                    ? item.getName()
                    : item.getName() + " (" + variant.getVariantName() + ")";

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "\"" + name + "\" has " + remaining.stripTrailingZeros().toPlainString()
                            + " left of what this channel was allocated.");
        }
    }

    @Override
    public void consume(
            Item item, ItemVariant variant, OrderChannel channel, BigDecimal baseQuantity) {
        if (item != null && !item.isStockTracked()) {
            return;
        }
        if (!ChannelStockMode.ALLOCATED.equals(modeOf(item))
                || channel == null
                || baseQuantity == null
                || baseQuantity.signum() <= 0) {
            return;
        }

        findAllocation(item, variant, channel).ifPresent(allocation -> {
            BigDecimal sold = allocation.getSoldQuantity() == null
                    ? BigDecimal.ZERO
                    : allocation.getSoldQuantity();

            allocation.setSoldQuantity(sold.add(baseQuantity));
            itemChannelStockRepository.save(allocation);
        });
    }

    /**
     * The allocation a sale on this channel draws on.
     *
     * An item with no options allocates against itself, which is the null
     * option — the same key the back office set it under.
     */
    private Optional<ItemChannelStock> findAllocation(
            Item item, ItemVariant variant, OrderChannel channel) {
        return salesChannelRepository.findByCode(channel.name())
                .flatMap(salesChannel -> itemChannelStockRepository.findOne(
                        item.getId(),
                        salesChannel.getId(),
                        variant == null ? null : variant.getId()));
    }

    private Item requireItem(UUID businessId, UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found"));

        if (item.getBusiness() == null || !Objects.equals(item.getBusiness().getId(), businessId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }

        return item;
    }

    /** An item that has never been split sells from the whole shelf everywhere. */
    private ChannelStockMode modeOf(Item item) {
        return item.getChannelStockMode() == null
                ? ChannelStockMode.SHARED
                : item.getChannelStockMode();
    }

    private ChannelStockAllocationResponse toResponse(ItemChannelStock allocation) {
        SalesChannel channel = allocation.getSalesChannel();
        ItemVariant variant = allocation.getVariant();

        return new ChannelStockAllocationResponse(
                channel.getId(),
                channel.getName(),
                channel.getCode(),
                variant == null ? null : variant.getId(),
                variant == null ? null : variant.getVariantName(),
                allocation.getQuantity(),
                allocation.getSoldQuantity());
    }

    private String lineKey(UUID channelId, UUID variantId) {
        return channelId + ":" + (variantId == null ? "" : variantId);
    }

    /**
     * The item-as-itself row, in a map that cannot hold a null key.
     *
     * A fixed UUID rather than a nullable key so the per-option totals below
     * can be summed in one pass, options and no-option alike.
     */
    private UUID nullVariantKey() {
        return NO_VARIANT;
    }

    private static final UUID NO_VARIANT = new UUID(0L, 0L);
}
