package kh.edu.istad.ite.features.inventory.service;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.AddOnRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemVariantRepository;
import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockBatchResponse;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.ItemUomConversion;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.inventory.entity.StockConsumption;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import kh.edu.istad.ite.features.inventory.entity.StockLayer;
import kh.edu.istad.ite.features.inventory.mapper.StockEntryMapper;
import kh.edu.istad.ite.features.inventory.repository.StockConsumptionRepository;
import kh.edu.istad.ite.features.inventory.repository.StockEntryRepository;
import kh.edu.istad.ite.features.inventory.repository.StockLayerRepository;
import kh.edu.istad.ite.shared.enums.StockEntryType;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import kh.edu.istad.ite.shared.cache.BusinessCacheEvictor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockEntryServiceImpl implements StockEntryService {

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(3);

    private final BusinessHelper businessHelper;
    private final ItemRepository itemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final AddOnRepository addOnRepository;
    private final StockEntryRepository stockEntryRepository;
    private final StockLayerRepository stockLayerRepository;
    private final StockConsumptionRepository stockConsumptionRepository;
    private final StockEntryMapper stockEntryMapper;
    private final BusinessCacheEvictor businessCacheEvictor;

    @Override
    @Transactional
    public StockEntryResponse createStockEntry(UUID businessId, CreateStockEntryRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        businessCacheEvictor.evictStorefront(businessId);
        StockTarget target = resolveTarget(
                businessId, request.itemId(), request.variantId(), request.addOnId());

        Optional<StockEntry> latestEntry = findLatestEntry(businessId, target);
        BigDecimal quantityBefore = latestEntry
                .map(StockEntry::getQuantityAfter)
                .orElse(ZERO_QUANTITY);
        BigDecimal quantityChange = normalizeQuantity(request.quantityChange());

        StockEntryType entryType = StockEntryType.valueOf(request.entryType());
        validateQuantityChange(entryType, quantityChange, latestEntry.isPresent());

        BigDecimal quantityAfter = quantityBefore.add(quantityChange).setScale(3, RoundingMode.HALF_UP);
        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock quantity cannot become negative");
        }

        boolean incoming = quantityChange.compareTo(BigDecimal.ZERO) > 0;
        /*
         * An adjustment that moves nothing: the count is already right and
         * what is being corrected is the cost or the batch details recorded
         * beside it. It is neither stock arriving nor stock leaving, so no
         * batch is opened and none is drawn down.
         */
        boolean standing = entryType == StockEntryType.ADJUSTMENT
                && quantityChange.compareTo(BigDecimal.ZERO) == 0;
        validateMoneyFields(entryType, incoming, standing, request);

        StockEntry stockEntry = new StockEntry();
        stockEntry.setBusiness(business);
        stockEntry.setItem(target.item());
        stockEntry.setVariant(target.variant());
        stockEntry.setAddOn(target.addOn());
        stockEntry.setEntryType(entryType);
        stockEntry.setQuantityChange(quantityChange);
        stockEntry.setQuantityBefore(quantityBefore);
        stockEntry.setQuantityAfter(quantityAfter);
        validateBatchFields(entryType, incoming, request);
        stockEntry.setLotNumber(TextHelper.trimToNull(request.lotNumber()));
        stockEntry.setManufacturedAt(request.manufacturedAt());
        stockEntry.setExpiresAt(request.expiresAt());
        stockEntry.setBatchData(request.batchData());
        stockEntry.setReferenceType(TextHelper.trimToNull(request.referenceType()));
        stockEntry.setReferenceId(request.referenceId());
        stockEntry.setReferenceNumber(TextHelper.trimToNull(request.referenceNumber()));
        stockEntry.setReason(TextHelper.trimToNull(request.reason()));
        stockEntry.setUnitSalePrice(normalizeUnitCost(request.unitSalePrice()));
        applyEnteredAmount(stockEntry, target, request, quantityChange);

        BigDecimal unitCost = normalizeUnitCost(request.unitCost());

        if (unitCost != null && entryType == StockEntryType.ADJUSTMENT) {
            List<StockLayer> openLayers = stockLayerRepository.findOpenLayers(
                    businessId,
                    target.itemId(),
                    target.variantId(),
                    target.addOnId()
            );
            for (StockLayer layer : openLayers) {
                layer.setUnitCost(unitCost);
                stockLayerRepository.save(layer);
            }
        }

        if (standing) {
            stockEntry.setUnitCost(unitCost);
            stockEntryRepository.saveAndFlush(stockEntry);
        } else if (incoming) {
            if (unitCost == null) {
                unitCost = currentUnitCost(businessId, target);
            }
            stockEntry.setUnitCost(unitCost);
            stockEntryRepository.saveAndFlush(stockEntry);
            openLayer(stockEntry, unitCost, quantityChange, receivedAt(request));
        } else {
            if (unitCost != null && entryType == StockEntryType.ADJUSTMENT) {
                stockEntry.setUnitCost(unitCost);
            }
            stockEntryRepository.saveAndFlush(stockEntry);
            consumeLayers(stockEntry, quantityChange.abs());
        }

        return stockEntryMapper.toResponse(stockEntryRepository.saveAndFlush(stockEntry));
    }

    /**
     * What a movement is against. Exactly one side is set: an item is sold on
     * its own, an add-on only ever rides along with one, and both are counted.
     *
     * On an item, an option narrows it further. An option holds a balance of
     * its own — a shop that runs out of Large has not run out of the item — so
     * the option is part of what identifies the balance, not a label on it.
     */
    private record StockTarget(Item item, ItemVariant variant, AddOn addOn) {
        UUID id() {
            return item != null ? item.getId() : addOn.getId();
        }

        String name() {
            if (item == null) {
                return addOn.getName();
            }

            return variant == null
                    ? item.getName()
                    : item.getName() + " — " + variant.getVariantName();
        }

        UUID itemId() {
            return item == null ? null : item.getId();
        }

        UUID variantId() {
            return variant == null ? null : variant.getId();
        }

        UUID addOnId() {
            return addOn == null ? null : addOn.getId();
        }
    }

    private StockTarget resolveTarget(UUID businessId, UUID itemId, UUID variantId, UUID addOnId) {
        if ((itemId == null) == (addOnId == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A stock entry is against either an item or an add-on"
            );
        }

        if (addOnId != null) {
            if (variantId != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "An add-on has no options to count separately"
                );
            }

            return new StockTarget(null, null, findAddOn(addOnId, businessId));
        }

        Item item = findItem(itemId, businessId);
        if (Boolean.FALSE.equals(item.getTrackInventory())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "\"" + item.getName() + "\" does not track inventory"
            );
        }
        ItemVariant variant = findVariant(variantId, item, businessId);

        if (variant == null && hasOptions(item) && !holdsUnassignedStock(businessId, item)) {
            // Stock on an item sold in options belongs to one of them. Left
            // unnamed it would join the pool held against the item as a whole,
            // which no option can be sold from and nobody would think to look
            // at.
            //
            // An item that already has such a pool — stock recorded before it
            // gained options — is the exception: that balance has to stay
            // correctable, or the quantity sits there forever with no way to
            // move it onto an option or write it off.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "\"" + item.getName() + "\" is sold in options — say which one this is for"
            );
        }

        return new StockTarget(item, variant, null);
    }

    private boolean hasOptions(Item item) {
        return item.getVariants() != null && !item.getVariants().isEmpty();
    }

    /** Whether anything is still counted against the item rather than an option. */
    private boolean holdsUnassignedStock(UUID businessId, Item item) {
        return stockEntryRepository
                .findFirstByBusiness_IdAndItem_IdAndVariantIsNullOrderByCreatedDateDescIdDesc(
                        businessId, item.getId())
                .map(StockEntry::getQuantityAfter)
                .filter(quantity -> quantity.compareTo(BigDecimal.ZERO) > 0)
                .isPresent();
    }

    private AddOn findAddOn(UUID addOnId, UUID businessId) {
        return addOnRepository.findByIdAndBusinessId(addOnId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Add-on has not been found"));
    }

    /**
     * The option a movement names, checked against the item it belongs to.
     *
     * An option from another item would open a balance chain nobody could
     * read, so it is refused rather than stored.
     */
    private ItemVariant findVariant(UUID variantId, Item item, UUID businessId) {
        if (variantId == null) {
            return null;
        }

        ItemVariant variant = itemVariantRepository.findByIdAndBusiness_Id(variantId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Option has not been found"));

        if (variant.getItem() == null || !variant.getItem().getId().equals(item.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "That option belongs to another item"
            );
        }

        return variant;
    }

    /**
     * The end of this target's balance chain.
     *
     * Each option counts on from its own last movement. Reading the item's
     * latest movement instead would hand a stock-in of Large whatever Small
     * happened to be left at.
     */
    private Optional<StockEntry> findLatestEntry(UUID businessId, StockTarget target) {
        if (target.item() == null) {
            return stockEntryRepository.findFirstByBusiness_IdAndAddOn_IdOrderByCreatedDateDescIdDesc(
                    businessId, target.addOnId());
        }

        return target.variant() == null
                ? stockEntryRepository.findFirstByBusiness_IdAndItem_IdAndVariantIsNullOrderByCreatedDateDescIdDesc(
                        businessId, target.itemId())
                : stockEntryRepository.findFirstByBusiness_IdAndItem_IdAndVariant_IdOrderByCreatedDateDescIdDesc(
                        businessId, target.itemId(), target.variantId());
    }

    /**
     * Cost belongs on the way in and sale price on the way out, and neither
     * crosses over. They shared one field once; a sale price typed into it
     * became the item's cost for every movement after it.
     *
     * An adjustment that moves no quantity is the one place a cost is allowed
     * outside an arrival: correcting the cost is the whole point of it.
     */
    private void validateMoneyFields(
            StockEntryType entryType,
            boolean incoming,
            boolean standing,
            CreateStockEntryRequest request
    ) {
        if (!incoming && !standing && entryType != StockEntryType.ADJUSTMENT && request.unitCost() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Outgoing stock is costed from what it was bought for, so unitCost cannot be set"
            );
        }
        if (incoming && request.unitSalePrice() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "unitSalePrice applies to stock leaving, not stock arriving"
            );
        }
        if (request.unitSalePrice() != null && entryType != StockEntryType.STOCK_OUT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "unitSalePrice applies only to a STOCK_OUT"
            );
        }

        /*
         * Stock arriving has to say what it cost.
         *
         * Everything downstream is built on it: what the shelf is worth, what
         * a sale cost, what a price is set against. Left out, it used to
         * inherit whatever stock cost before — and zero when there was no
         * before, which reads as free stock rather than as nobody having said.
         * Zero is still allowed, typed on purpose: donated and sample stock is
         * real. What is refused is not saying.
         *
         * An adjustment is exempt. It corrects a count, not a price, and the
         * stock it adds back is the stock that was already there.
         */
        boolean receipt = entryType == StockEntryType.STOCK_IN
                || entryType == StockEntryType.OPENING_STOCK;

        if (receipt && request.unitCost() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Say what this stock cost per unit. Enter 0 if it was free."
            );
        }
    }

    /**
     * Stores the amount as it was counted, in the unit it was counted in.
     *
     * The unit has to be one this item is actually measured in — its base unit
     * or one of its conversions — and the converted amount has to match the
     * base quantity being applied, or the ledger would read back a movement
     * that never happened.
     */
    private void applyEnteredAmount(
            StockEntry stockEntry,
            StockTarget target,
            CreateStockEntryRequest request,
            BigDecimal quantityChange
    ) {
        if (request.unitId() == null && request.enteredQuantity() == null) {
            return;
        }
        if (request.unitId() == null || request.enteredQuantity() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "enteredQuantity and unitId go together"
            );
        }

        BigDecimal factor = conversionFactor(target, request.unitId());
        BigDecimal expected = request.enteredQuantity().abs()
                .multiply(factor)
                .setScale(3, RoundingMode.HALF_UP);

        if (expected.compareTo(quantityChange.abs()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "enteredQuantity does not convert to the quantity change"
            );
        }

        stockEntry.setEnteredQuantity(request.enteredQuantity().abs().setScale(3, RoundingMode.HALF_UP));
        stockEntry.setEnteredUnit(resolveEnteredUnit(target, request.unitId()));
    }

    /**
     * Base units per one of the given unit. The base unit itself is 1.
     *
     * An add-on carries a base unit but no conversions of its own yet, so only
     * its base unit is accepted.
     */
    private BigDecimal conversionFactor(StockTarget target, UUID unitId) {
        if (target.addOn() != null) {
            if (target.addOn().getBaseUnit() != null
                    && target.addOn().getBaseUnit().getId().equals(unitId)) {
                return BigDecimal.ONE;
            }

            return target.addOn().getUomConversions().stream()
                    .filter(conversion -> conversion.getUnit().getId().equals(unitId))
                    .map(kh.edu.istad.ite.features.catalog.entity.AddOnUomConversion::getFactor)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "That unit is not one this add-on is measured in"
                    ));
        }

        Item item = target.item();
        if (item.getUnit() != null && item.getUnit().getId().equals(unitId)) {
            return BigDecimal.ONE;
        }

        return item.getUomConversions().stream()
                .filter(conversion -> conversion.getUnit().getId().equals(unitId))
                .map(ItemUomConversion::getFactor)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "That unit is not one this item is measured in"
                ));
    }

    private Unit resolveEnteredUnit(StockTarget target, UUID unitId) {
        if (target.addOn() != null) {
            if (target.addOn().getBaseUnit() != null
                    && target.addOn().getBaseUnit().getId().equals(unitId)) {
                return target.addOn().getBaseUnit();
            }

            return target.addOn().getUomConversions().stream()
                    .filter(conversion -> conversion.getUnit().getId().equals(unitId))
                    .map(kh.edu.istad.ite.features.catalog.entity.AddOnUomConversion::getUnit)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "That unit is not one this add-on is measured in"
                    ));
        }

        Item item = target.item();
        if (item.getUnit() != null && item.getUnit().getId().equals(unitId)) {
            return item.getUnit();
        }

        return item.getUomConversions().stream()
                .filter(conversion -> conversion.getUnit().getId().equals(unitId))
                .map(ItemUomConversion::getUnit)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "That unit is not one this item is measured in"
                ));
    }

    /**
     * When a delivery arrived: what was said, or now if nothing was.
     *
     * A delivery recorded two days late still belongs where it happened in the
     * queue. A date in the future is refused — it would put the batch behind
     * stock that has not been bought yet, and there is no reading of it that
     * is not a typo.
     */
    private LocalDateTime receivedAt(CreateStockEntryRequest request) {
        if (request.receivedAt() == null) {
            return LocalDateTime.now();
        }
        if (request.receivedAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stock cannot have arrived in the future"
            );
        }

        return request.receivedAt();
    }

    /**
     * Lot and dates belong to stock arriving, the same way a cost does.
     *
     * On a sale or a stock-out there is no batch to describe: which one the
     * stock left is worked out from the queue, not typed. Recording an expiry
     * there would read as a fact about the movement and be one about nothing.
     *
     * An adjustment is exempt whichever way it moves. Correcting what a batch
     * is — a lot number keyed in wrong, a date read off the carton at
     * stocktake — is a thing an adjustment is for, and refusing it on a
     * correction that also counts a few units off would leave the operator
     * splitting one real event into two movements to record it.
     */
    private void validateBatchFields(
            StockEntryType entryType,
            boolean incoming,
            CreateStockEntryRequest request
    ) {
        boolean describesBatch = request.lotNumber() != null
                || request.manufacturedAt() != null
                || request.expiresAt() != null
                || request.receivedAt() != null;

        if (describesBatch && !incoming && entryType != StockEntryType.ADJUSTMENT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lot and expiry describe stock arriving, not stock leaving"
            );
        }

        if (request.manufacturedAt() != null
                && request.expiresAt() != null
                && request.expiresAt().isBefore(request.manufacturedAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This batch expires before it was made — check the dates"
            );
        }

        /*
         * A batch cannot be put on the shelf already expired.
         *
         * A date behind us is a typo every time, and an expensive one: the
         * queue is ordered by expiry, so the batch would go straight to the
         * front and be the next thing sold. Stock that really has gone off is
         * written off, not received.
         *
         * Today is allowed — it expires at the end of the day, not the start.
         */
        if (request.expiresAt() != null && request.expiresAt().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "That expiry date has already passed. Write the stock off instead."
            );
        }

        if (request.manufacturedAt() != null && request.manufacturedAt().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stock cannot have been made in the future"
            );
        }
    }

    private void openLayer(
            StockEntry stockEntry,
            BigDecimal unitCost,
            BigDecimal quantity,
            LocalDateTime receivedAt
    ) {
        StockLayer layer = new StockLayer();
        layer.setBusiness(stockEntry.getBusiness());
        layer.setItem(stockEntry.getItem());
        layer.setVariant(stockEntry.getVariant());
        layer.setAddOn(stockEntry.getAddOn());
        layer.setSourceEntry(stockEntry);
        layer.setUnitCost(unitCost == null ? BigDecimal.ZERO.setScale(2) : unitCost);
        layer.setQuantityReceived(quantity);
        layer.setQuantityRemaining(quantity);
        layer.setReceivedAt(receivedAt);
        layer.setLotNumber(stockEntry.getLotNumber());
        layer.setManufacturedAt(stockEntry.getManufacturedAt());
        layer.setExpiresAt(stockEntry.getExpiresAt());
        layer.setBatchData(stockEntry.getBatchData());
        stockLayerRepository.saveAndFlush(layer);
    }

    /**
     * Takes the quantity from the batches nearest their date first — then the
     * oldest of those that never expire — and records what each one gave up,
     * then writes the movement's own cost from the total.
     *
     * A batch already past its date is still drawn from. The stock is on the
     * shelf and the balance says so; refusing the movement would stop a till
     * mid-sale over a date nobody has got round to writing off. It is flagged
     * where the batches are read instead.
     *
     * If the layers do not cover it — the item was stocked before batches were
     * kept — the shortfall is costed at whatever the item costs now. Refusing
     * the movement would be worse: the balance says the stock is there, and a
     * sale should not fail over a gap in history.
     */
    private void consumeLayers(StockEntry stockEntry, BigDecimal quantity) {
        BigDecimal outstanding = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;

        List<StockLayer> layers = stockLayerRepository.findOpenLayers(
                stockEntry.getBusiness().getId(),
                stockEntry.getItem() == null ? null : stockEntry.getItem().getId(),
                stockEntry.getVariant() == null ? null : stockEntry.getVariant().getId(),
                stockEntry.getAddOn() == null ? null : stockEntry.getAddOn().getId()
        );

        for (StockLayer layer : layers) {
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal taken = layer.getQuantityRemaining().min(outstanding);
            layer.setQuantityRemaining(layer.getQuantityRemaining().subtract(taken));
            stockLayerRepository.save(layer);

            StockConsumption consumption = new StockConsumption();
            consumption.setStockEntry(stockEntry);
            consumption.setStockLayer(layer);
            consumption.setQuantity(taken);
            consumption.setUnitCost(layer.getUnitCost());
            stockConsumptionRepository.save(consumption);

            totalCost = totalCost.add(taken.multiply(layer.getUnitCost()));
            outstanding = outstanding.subtract(taken);
        }

        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal fallback = currentUnitCost(
                    stockEntry.getBusiness().getId(),
                    new StockTarget(
                            stockEntry.getItem(),
                            stockEntry.getVariant(),
                            stockEntry.getAddOn()
                    )
            );
            totalCost = totalCost.add(outstanding.multiply(fallback));
        }

        totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);
        stockEntry.setCostOfGoods(totalCost);
        stockEntry.setUnitCost(
                quantity.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO.setScale(2)
                        : totalCost.divide(quantity, 2, RoundingMode.HALF_UP)
        );
    }

    /** What the next unit out will cost: the batch at the head of the queue. */
    private BigDecimal currentUnitCost(UUID businessId, StockTarget target) {
        return stockLayerRepository
                .findOpenLayers(businessId, target.itemId(), target.variantId(), target.addOnId())
                .stream()
                .findFirst()
                .map(StockLayer::getUnitCost)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    @Override
    @Transactional
    public StockEntry recordSale(
            Business business,
            Item item,
            ItemVariant variant,
            BigDecimal quantity,
            UUID orderId,
            String invoiceNumber
    ) {
        return recordSale(business, item, variant, quantity, null, null, orderId, invoiceNumber);
    }

    /**
     * A sale, in whatever unit it was rung up in.
     *
     * The shelf only ever moves in base units, so a case of twenty-four leaves
     * one movement of −24. What the customer actually bought is kept beside it
     * — "1 case" — the same way a stock-in records "2 sacks" over the grams it
     * put away. One movement, readable as what happened.
     */
    @Override
    @Transactional
    public StockEntry recordSale(
            Business business,
            Item item,
            ItemVariant variant,
            BigDecimal quantity,
            BigDecimal soldQuantity,
            Unit soldUnit,
            UUID orderId,
            String invoiceNumber
    ) {
        if (item != null && !item.isStockTracked()) {
            StockEntry emptyEntry = new StockEntry();
            emptyEntry.setBusiness(business);
            emptyEntry.setItem(item);
            emptyEntry.setVariant(variant);
            emptyEntry.setUnitCost(BigDecimal.ZERO);
            return emptyEntry;
        }
        // The till knows which option was rung up, so the sale comes off that
        // option's count. A line with none — an item sold without options, or
        // one rung up before it had any — falls to the item's own balance
        // rather than being refused at the till.
        StockTarget target = new StockTarget(item, variant, null);
        Optional<StockEntry> latestEntry = findLatestEntry(business.getId(), target);

        BigDecimal quantityBefore = latestEntry.map(StockEntry::getQuantityAfter).orElse(ZERO_QUANTITY);
        BigDecimal quantityChange = normalizeQuantity(quantity).negate();
        BigDecimal quantityAfter = quantityBefore.add(quantityChange).setScale(3, RoundingMode.HALF_UP);

        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Not enough stock for \"" + target.name() + "\": " + quantityBefore + " left");
        }

        StockEntry stockEntry = new StockEntry();
        stockEntry.setBusiness(business);
        stockEntry.setItem(item);
        stockEntry.setVariant(variant);
        stockEntry.setEntryType(StockEntryType.SALE);
        stockEntry.setQuantityChange(quantityChange);
        stockEntry.setQuantityBefore(quantityBefore);
        stockEntry.setQuantityAfter(quantityAfter);
        // The reference is what lets a shop trace a movement back to the till.
        // What was rung up, before the pack was unpacked into base units.
        if (soldUnit != null && soldQuantity != null) {
            stockEntry.setEnteredQuantity(soldQuantity.abs().setScale(3, RoundingMode.HALF_UP));
            stockEntry.setEnteredUnit(soldUnit);
        }
        stockEntry.setReferenceType("ORDER");
        stockEntry.setReferenceId(orderId);
        stockEntry.setReferenceNumber(invoiceNumber);

        stockEntryRepository.saveAndFlush(stockEntry);
        // Cost comes from the batches this sale actually emptied, so the entry
        // carries its own margin rather than borrowing the last known price.
        consumeLayers(stockEntry, quantityChange.abs());

        return stockEntryRepository.saveAndFlush(stockEntry);
    }

    /**
     * An add-on leaving with a sale.
     *
     * Counted like anything else it is sold beside: its own balance, its own
     * batches, its own cost of goods. A tub of pearls empties whether it was
     * scooped into one drink or ten, and until now nothing took it off the
     * shelf but a hand-typed movement.
     */
    @Override
    @Transactional
    public StockEntry recordAddOnSale(
            Business business,
            AddOn addOn,
            BigDecimal quantity,
            UUID orderId,
            String invoiceNumber
    ) {
        StockTarget target = new StockTarget(null, null, addOn);
        Optional<StockEntry> latestEntry = findLatestEntry(business.getId(), target);

        BigDecimal quantityBefore = latestEntry.map(StockEntry::getQuantityAfter).orElse(ZERO_QUANTITY);
        BigDecimal quantityChange = normalizeQuantity(quantity).negate();
        BigDecimal quantityAfter = quantityBefore.add(quantityChange).setScale(3, RoundingMode.HALF_UP);

        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Not enough \"" + addOn.getName() + "\" left: " + quantityBefore);
        }

        StockEntry stockEntry = new StockEntry();
        stockEntry.setBusiness(business);
        stockEntry.setAddOn(addOn);
        stockEntry.setEntryType(StockEntryType.SALE);
        stockEntry.setQuantityChange(quantityChange);
        stockEntry.setQuantityBefore(quantityBefore);
        stockEntry.setQuantityAfter(quantityAfter);
        stockEntry.setReferenceType("ORDER");
        stockEntry.setReferenceId(orderId);
        stockEntry.setReferenceNumber(invoiceNumber);

        stockEntryRepository.saveAndFlush(stockEntry);
        consumeLayers(stockEntry, quantityChange.abs());

        return stockEntryRepository.saveAndFlush(stockEntry);
    }

    /**
     * What one unit of this item currently costs — the batch at the head of
     * the queue, which is the one the next sale will draw from.
     *
     * It used to read the most recent movement's `unitCost`, which meant a
     * hand-typed stock-out set the cost of everything sold afterwards.
     *
     * The head of the queue, not the oldest delivery: a short-dated batch that
     * arrived yesterday is what the next sale will actually cost.
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal findLatestUnitCost(UUID businessId, UUID itemId) {
        // Item-wide: the oldest batch still holding stock, whichever option it
        // was received for.
        return stockLayerRepository.findOpenItemLayers(businessId, itemId)
                .stream()
                .findFirst()
                .map(StockLayer::getUnitCost)
                .orElseGet(() -> stockEntryRepository.findAllByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(businessId, itemId)
                        .stream()
                        .map(StockEntry::getUnitCost)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(BigDecimal.ZERO.setScale(2)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockEntryResponse> findAllStockEntries(
            UUID businessId,
            UUID itemId,
            StockEntryType entryType,
            String referenceType,
            UUID referenceId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        businessHelper.findOwnedBusiness(businessId);
        if (itemId != null) {
            findItem(itemId, businessId);
        }

        Specification<StockEntry> specification = stockEntrySpecification(
                businessId,
                itemId,
                entryType,
                TextHelper.trimToNull(referenceType),
                referenceId,
                from,
                to
        );

        return stockEntryRepository.findAll(
                        specification,
                        Sort.by(Sort.Direction.DESC, "createdDate", "id")
                )
                .stream()
                .map(stockEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockEntryResponse findStockEntryById(UUID businessId, UUID stockEntryId) {
        businessHelper.findOwnedBusiness(businessId);
        StockEntry stockEntry = findStockEntry(stockEntryId, businessId);

        // Only here. A movement's own cost is a single number, and the batches
        // behind it are what makes that number explicable — but only to
        // somebody who has opened this one movement to ask.
        return stockEntryMapper.toResponse(
                stockEntry,
                stockConsumptionRepository.findBreakdown(stockEntry.getId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockEntryResponse> findItemStockEntries(UUID businessId, UUID itemId) {
        businessHelper.findOwnedBusiness(businessId);
        findItem(itemId, businessId);

        return stockEntryRepository.findAllByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(businessId, itemId)
                .stream()
                .map(stockEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * One balance per thing that holds one: every add-on, every item without
     * options, and every option of an item that has them.
     *
     * An item sold in options appears once per option rather than once in
     * total. Its total is a sum, and summing here would hide which option the
     * stock is actually in — the question the screen is asking.
     */
    public List<StockSummaryResponse> findCurrentStock(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);
        Map<StockEntry.TargetKey, StockValue> valueByTarget = openStockValues(businessId);
        Map<StockEntry.TargetKey, StockSummaryResponse> currentStockByTarget = new LinkedHashMap<>();
        Map<StockEntry.TargetKey, BigDecimal> fallbackUnitCostByTarget = new LinkedHashMap<>();

        stockEntryRepository.findAllByBusiness_IdOrderByCreatedDateDescIdDesc(businessId)
                .forEach(stockEntry -> {
                    if (stockEntry.getUnitCost() != null) {
                        fallbackUnitCostByTarget.putIfAbsent(stockEntry.getTargetKey(), stockEntry.getUnitCost());
                    }
                    currentStockByTarget.computeIfAbsent(
                            stockEntry.getTargetKey(),
                            key -> {
                                StockValue value = valueByTarget.get(key);
                                BigDecimal unitCost = (value != null && value.unitCost() != null)
                                        ? value.unitCost()
                                        : fallbackUnitCostByTarget.get(key);

                                return stockEntryMapper.toSummary(
                                        stockEntry,
                                        value == null ? null : value.value(),
                                        unitCost
                                );
                            }
                    );
                });

        return List.copyOf(currentStockByTarget.values());
    }

    /** What one target's remaining stock is worth, and what its next unit costs. */
    private record StockValue(BigDecimal value, BigDecimal unitCost) {
    }

    /**
     * What every target's stock is worth, read off the batches still open.
     *
     * Each delivery kept the price it arrived at, so the value of what is left
     * is the sum across those batches rather than one cost times a quantity —
     * two deliveries at different prices are both on the shelf, and no single
     * multiplier is right for them. The unit cost quoted alongside is the head
     * of the queue's, which is what the next unit out will be costed at.
     */
    private Map<StockEntry.TargetKey, StockValue> openStockValues(UUID businessId) {
        Map<StockEntry.TargetKey, BigDecimal> totals = new LinkedHashMap<>();
        Map<StockEntry.TargetKey, BigDecimal> oldestCost = new LinkedHashMap<>();

        // In consumption order, so the first batch seen for a target is the
        // one the next sale will draw from.
        for (StockLayer layer : stockLayerRepository.findAllOpenLayers(businessId)) {
            StockEntry.TargetKey key = new StockEntry.TargetKey(
                    layer.getItem() == null ? null : layer.getItem().getId(),
                    layer.getVariant() == null ? null : layer.getVariant().getId(),
                    layer.getAddOn() == null ? null : layer.getAddOn().getId()
            );

            totals.merge(
                    key,
                    layer.getQuantityRemaining().multiply(layer.getUnitCost()),
                    BigDecimal::add
            );
            oldestCost.putIfAbsent(key, layer.getUnitCost());
        }

        Map<StockEntry.TargetKey, StockValue> values = new LinkedHashMap<>();
        totals.forEach((key, total) -> values.put(
                key,
                new StockValue(total.setScale(2, RoundingMode.HALF_UP), oldestCost.get(key))
        ));

        return values;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockBatchResponse> findItemBatches(UUID businessId, UUID itemId) {
        businessHelper.findOwnedBusiness(businessId);
        findItem(itemId, businessId);

        List<StockLayer> layers = stockLayerRepository.findOpenItemLayers(businessId, itemId);
        List<StockBatchResponse> batches = new java.util.ArrayList<>(layers.size());
        int position = 1;

        // Already in consumption order — soonest to expire, then oldest — so
        // the position is simply where each one sits in the answer.
        for (StockLayer layer : layers) {
            BigDecimal unitCost = layer.getUnitCost() == null
                    ? BigDecimal.ZERO
                    : layer.getUnitCost();
            BigDecimal remaining = layer.getQuantityRemaining() == null
                    ? BigDecimal.ZERO
                    : layer.getQuantityRemaining();

            batches.add(new StockBatchResponse(
                    layer.getId(),
                    layer.getVariant() == null ? null : layer.getVariant().getId(),
                    layer.getVariant() == null ? null : layer.getVariant().getVariantName(),
                    unitCost,
                    layer.getQuantityReceived(),
                    remaining,
                    remaining.multiply(unitCost).setScale(2, RoundingMode.HALF_UP),
                    layer.getReceivedAt(),
                    layer.getLotNumber(),
                    layer.getManufacturedAt(),
                    layer.getExpiresAt(),
                    layer.isExpired(),
                    position++));
        }

        return batches;
    }

    @Override
    public StockSummaryResponse findCurrentStockByItem(UUID businessId, UUID itemId) {
        businessHelper.findOwnedBusiness(businessId);
        findItem(itemId, businessId);

        return itemTotal(businessId, itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public StockSummaryResponse findAvailableStock(UUID businessId, UUID itemId) {
        return itemTotal(businessId, itemId);
    }

    /**
     * What can still be sold of one option — or of the item, when a line names
     * no option.
     *
     * A basket asks this before it lets a line grow. The item's total cannot
     * answer it: a shop with ten Smalls and no Larges has plenty of the item
     * and none of what the customer is trying to buy.
     */
    @Override
    @Transactional(readOnly = true)
    public StockSummaryResponse findAvailableStock(UUID businessId, UUID itemId, UUID variantId) {
        if (variantId == null) {
            return itemTotal(businessId, itemId);
        }

        return stockEntryRepository
                .findFirstByBusiness_IdAndItem_IdAndVariant_IdOrderByCreatedDateDescIdDesc(
                        businessId, itemId, variantId)
                .map(stockEntryMapper::toSummary)
                .orElseGet(() -> {
                    // No movement has ever named this option. If the item is
                    // tracked at all, the option holds nothing; if it is not,
                    // the caller must go on reading it as untracked.
                    StockSummaryResponse itemSummary = itemTotal(businessId, itemId);

                    return itemSummary.lastEntryId() == null
                            ? itemSummary
                            : stockEntryMapper.emptyVariantSummary(itemId, variantId, itemSummary.lastEntryId());
                });
    }

    /**
     * What an item holds altogether: every option's balance, plus whatever is
     * still held against the item as a whole.
     *
     * Readers that ask about the item rather than an option — a storefront
     * deciding whether it can be added to a basket — must not be handed one
     * option's chain. Before options existed the item had a single chain and
     * its last entry was the answer; now that answer is a sum.
     */
    private StockSummaryResponse itemTotal(UUID businessId, UUID itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item != null && !item.isStockTracked()) {
            return stockEntryMapper.emptySummary(itemId);
        }

        List<StockEntry> entries = stockEntryRepository
                .findAllByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(businessId, itemId);

        if (entries.isEmpty()) {
            return stockEntryMapper.emptySummary(itemId);
        }

        Map<StockEntry.TargetKey, StockEntry> latestByTarget = new LinkedHashMap<>();
        entries.forEach(stockEntry -> latestByTarget.putIfAbsent(stockEntry.getTargetKey(), stockEntry));

        BigDecimal total = latestByTarget.values().stream()
                .map(StockEntry::getQuantityAfter)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);

        // Newest first, so the head is the item's most recent movement.
        StockEntry latest = entries.get(0);

        // Worth the sum of every open batch on the item, whichever option it
        // arrived for, at the price each was bought at.
        List<StockLayer> openLayers = stockLayerRepository.findOpenItemLayers(businessId, itemId);
        BigDecimal stockValue = openLayers.stream()
                .map(layer -> layer.getQuantityRemaining().multiply(layer.getUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitCost = openLayers.stream()
                .findFirst()
                .map(StockLayer::getUnitCost)
                .orElseGet(() -> entries.stream()
                        .map(StockEntry::getUnitCost)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));

        return stockEntryMapper.itemTotal(
                itemId, total, stockValue, unitCost, latest.getId(), latest.getCreatedDate());
    }

    private Item findItem(UUID itemId, UUID businessId) {
        return itemRepository.findByIdAndBusinessId(itemId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item has not been found"));
    }

    private StockEntry findStockEntry(UUID stockEntryId, UUID businessId) {
        return stockEntryRepository.findByIdAndBusiness_Id(stockEntryId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock entry has not been found"));
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeUnitCost(BigDecimal unitCost) {
        if (unitCost == null) {
            return null;
        }
        if (unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit cost must be at least zero");
        }
        return unitCost.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateQuantityChange(
            StockEntryType entryType,
            BigDecimal quantityChange,
            boolean itemHasStockEntries
    ) {
        /*
         * A movement moves stock, so zero says nothing — with one exception.
         *
         * An adjustment is also how a cost or a batch detail gets corrected on
         * stock that is already counted right. That correction has no quantity
         * to it, and refusing it would leave the operator inventing a change
         * of one up and one down to record what they actually meant.
         */
        if (quantityChange.compareTo(BigDecimal.ZERO) == 0
                && entryType != StockEntryType.ADJUSTMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity change cannot be zero");
        }

        switch (entryType) {
            case OPENING_STOCK -> {
                if (itemHasStockEntries) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Opening stock already exists for this item");
                }
                if (quantityChange.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening stock must be at least zero");
                }
            }
            case STOCK_IN, RETURN -> requirePositiveQuantity(quantityChange, entryType);
            case STOCK_OUT, SALE -> requireNegativeQuantity(quantityChange, entryType);
            case ADJUSTMENT -> {
            }
        }
    }

    private void requirePositiveQuantity(BigDecimal quantityChange, StockEntryType entryType) {
        if (quantityChange.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entryType + " quantity change must be positive");
        }
    }

    private void requireNegativeQuantity(BigDecimal quantityChange, StockEntryType entryType) {
        if (quantityChange.compareTo(BigDecimal.ZERO) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entryType + " quantity change must be negative");
        }
    }

    private Specification<StockEntry> stockEntrySpecification(
            UUID businessId,
            UUID itemId,
            StockEntryType entryType,
            String referenceType,
            UUID referenceId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("business").get("id"), businessId));

            if (itemId != null) {
                predicates.add(criteriaBuilder.equal(root.get("item").get("id"), itemId));
            }
            if (entryType != null) {
                predicates.add(criteriaBuilder.equal(root.get("entryType"), entryType));
            }
            if (referenceType != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("referenceType")),
                        referenceType.toLowerCase()
                ));
            }
            if (referenceId != null) {
                predicates.add(criteriaBuilder.equal(root.get("referenceId"), referenceId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdDate"), to));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
