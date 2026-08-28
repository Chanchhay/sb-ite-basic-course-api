package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemColorRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.catalog.service.ItemGroupService;
import kh.edu.istad.ite.features.catalog.service.ItemService;
import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.inventory.repository.StockEntryRepository;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ItemImportCommitter implements ImportCommitter {

    private final ItemService itemService;
    private final ItemGroupService itemGroupService;
    private final ItemGroupRepository itemGroupRepository;
    private final UnitRepository unitRepository;
    private final StockEntryRepository stockEntryRepository;
    private final OpeningStockPoster openingStockPoster;

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.ITEM;
    }

    @Override
    public CommitOutcome commit(
            ImportJob job,
            ImportRecord record,
            UUID matchedEntityId,
            MappingPlan plan
    ) {
        ItemImportRecord item = (ItemImportRecord) record;
        UUID businessId = job.getBusiness().getId();

        if (matchedEntityId != null && plan.duplicateStrategy() != ImportDuplicateStrategy.UPDATE_EXISTING) {
            return CommitOutcome.skipped(matchedEntityId);
        }

        UUID unitId = resolveUnitId(businessId, item, plan);

        if (unitId == null) {
            return CommitOutcome.failed(
                    item.unitName() == null
                            ? "No unit was chosen for this import."
                            : "\"" + item.unitName() + "\" is no longer one of your units."
            );
        }

        ResolvedGroup group = resolveItemGroup(businessId, item);

        if (matchedEntityId != null) {
            return update(businessId, matchedEntityId, item, group, unitId, job);
        }

        return create(businessId, item, group, unitId, job);
    }

    private CommitOutcome create(
            UUID businessId,
            ItemImportRecord item,
            ResolvedGroup group,
            UUID unitId,
            ImportJob job
    ) {
        ItemResponse created = itemService.createItem(
                businessId,
                new CreateItemRequest(
                        group.id(),
                        unitId,
                        item.name(),
                        item.sku(),
                        null,
                        item.description(),
                        null,
                        null,
                        item.badge(),
                        item.barcode(),
                        item.price(),
                        item.itemType(),
                        item.trackInventory(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        item.lowStockLevel(),
                        item.status()
                ),
                null
        );

        UUID stockEntryId = postOpeningStock(businessId, created, item, job);

        return CommitOutcome.created(created.id(), stockEntryId, createdGroupIds(group));
    }

    /**
     * Rewrites only what the file actually carried.
     *
     * Every field left unmatched arrives here as null, and the catalogue's
     * update leaves nulls alone — so a file of names and prices refreshes
     * names and prices and does not wipe the descriptions and barcodes the
     * shop has typed in since.
     */
    private CommitOutcome update(
            UUID businessId,
            UUID itemId,
            ItemImportRecord item,
            ResolvedGroup group,
            UUID unitId,
            ImportJob job
    ) {
        ItemResponse updated = itemService.updateItem(
                businessId,
                itemId,
                new UpdateItemRequest(
                        group.id(),
                        unitId,
                        item.name(),
                        item.sku(),
                        null,
                        item.description(),
                        null,
                        null,
                        item.badge(),
                        item.barcode(),
                        item.price(),
                        item.itemType(),
                        item.trackInventory(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        item.lowStockLevel(),
                        item.status()
                ),
                null
        );

        UUID stockEntryId = postOpeningStock(businessId, updated, item, job);

        return CommitOutcome.updated(updated.id(), stockEntryId, createdGroupIds(group));
    }

    /**
     * Posts the row's starting quantity, if it carried one and the item has no
     * stock history yet.
     *
     * The history check matters on the update path: a shop re-importing its
     * price list means to refresh prices, not to open a second balance on
     * items it has been selling from all week. The ledger would refuse it
     * anyway; catching it here keeps the row a clean update rather than a
     * failure.
     */
    private UUID postOpeningStock(
            UUID businessId,
            ItemResponse item,
            ItemImportRecord record,
            ImportJob job
    ) {
        if (!record.hasOpeningStock() || !Boolean.TRUE.equals(item.trackInventory())) {
            return null;
        }

        boolean alreadyCounted = stockEntryRepository
                .findFirstByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(businessId, item.id())
                .isPresent();

        if (alreadyCounted) {
            return null;
        }

        return openingStockPoster.post(
                businessId,
                item.id(),
                record.openingStock(),
                record.costPrice(),
                job.getId(),
                job.getSourceFileName()
        );
    }

    /**
     * The row's category, created if the shop does not have it yet.
     *
     * This is the one place an import brings something into being that the
     * user did not explicitly ask for, and it is what makes migrating into an
     * empty catalogue work at all: an export names its categories in a column
     * and nowhere else. The preview says how many will appear before any of
     * them do.
     */
    private ResolvedGroup resolveItemGroup(UUID businessId, ItemImportRecord item) {
        ItemGroup existing = itemGroupRepository
                .findFirstByBusinessIdAndNameIgnoreCase(businessId, item.itemGroupName())
                .orElse(null);

        /*
         * A category that already exists is used exactly as it stands, parent
         * and all. Checking has already warned when the file disagrees about
         * where it sits: re-filing it here would move every item the shop had
         * in it, which is not what importing a price list means.
         */
        if (existing != null) {
            return new ResolvedGroup(existing.getId(), false);
        }

        UUID createdId = itemGroupService
                .createItemGroup(
                        businessId,
                        new CreateItemGroupRequest(
                                item.itemGroupName(),
                                null,
                                resolveParentGroupId(businessId, item.parentGroupName())))
                .id();

        return new ResolvedGroup(createdId, true);
    }

    /**
     * The parent a new category goes under, created in turn if the file named
     * one the shop has not got.
     *
     * Null when the file named no parent, which is the flat case and most of
     * them. The two-level limit is the catalogue's to enforce, and checking has
     * already refused a row naming a parent that is itself a sub-category.
     */
    private UUID resolveParentGroupId(UUID businessId, String parentName) {
        if (parentName == null) {
            return null;
        }

        return itemGroupRepository
                .findFirstByBusinessIdAndNameIgnoreCase(businessId, parentName)
                .map(ItemGroup::getId)
                .orElseGet(() -> itemGroupService
                        .createItemGroup(businessId, new CreateItemGroupRequest(parentName, null, null))
                        .id());
    }

    /**
     * The unit named on the row, or the one chosen for the whole file.
     *
     * Checking already established that a named unit was one the shop may use,
     * so a miss here means it was removed while the import sat waiting for
     * approval. That returns null and fails the row: falling back to the
     * file's default would file the item under a measure nobody chose for it,
     * and an item's unit is not something to guess at after the fact.
     */
    private UUID resolveUnitId(UUID businessId, ItemImportRecord item, MappingPlan plan) {
        if (item.unitName() == null) {
            return plan.defaultUnitId();
        }

        return unitRepository.findSelectableUnitsNamed(businessId, item.unitName())
                .stream()
                .findFirst()
                .map(Unit::getId)
                .orElse(null);
    }

    /** Whether this option has a stock history already, and so an opening balance. */
    private boolean alreadyCounted(UUID businessId, UUID itemId, UUID variantId) {
        return stockEntryRepository
                .findFirstByBusiness_IdAndItem_IdAndVariant_IdOrderByCreatedDateDescIdDesc(
                        businessId, itemId, variantId)
                .isPresent();
    }

    /** A category id, and whether this import is what brought it into being. */
    private record ResolvedGroup(UUID id, boolean created) {
    }

    private static List<UUID> createdGroupIds(ResolvedGroup group) {
        return group.created() ? List.of(group.id()) : List.of();
    }

    // --- items sold in options ---------------------------------------------------

    /**
     * Creates one item from the several rows that describe its options.
     *
     * It has to be one call. The catalogue takes an item's options as a set and
     * replaces them wholesale, so adding them a row at a time would leave each
     * row wiping out the one before it. That is why the rows are gathered before
     * anything is written rather than committed as they come.
     *
     * The identifiers move down a level: on a variant export the SKU and
     * barcode columns describe the option, not the product, so they are given
     * to the option and the item takes the group key as its own code.
     *
     * @param records the group's rows in file order; the first carries the item
     */
    public GroupCommitOutcome commitOptionGroup(
            ImportJob job,
            List<OptionRow> rows,
            UUID matchedEntityId,
            MappingPlan plan
    ) {
        List<ItemImportRecord> records = rows.stream().map(OptionRow::record).toList();
        ItemImportRecord head = records.getFirst();
        UUID businessId = job.getBusiness().getId();

        if (matchedEntityId != null && plan.duplicateStrategy() != ImportDuplicateStrategy.UPDATE_EXISTING) {
            return GroupCommitOutcome.of(ImportRowStatus.SKIPPED, matchedEntityId, List.of(), Map.of());
        }

        UUID unitId = resolveUnitId(businessId, head, plan);

        if (unitId == null) {
            return GroupCommitOutcome.failed(
                    head.unitName() == null
                            ? "No unit was chosen for this import."
                            : "\"" + head.unitName() + "\" is no longer one of your units."
            );
        }

        ResolvedGroup group = resolveItemGroup(businessId, head);

        boolean asColours = sellsByColour(records);
        List<ItemColorRequest> colours = asColours ? coloursOf(records) : List.of();
        List<ItemVariantRequest> options = optionsOf(records, asColours);

        CreateItemRequest request = new CreateItemRequest(
                group.id(),
                unitId,
                head.name(),
                head.groupKey(),
                null,
                head.description(),
                head.imageUrl(),
                null,
                head.badge(),
                null,
                head.price(),
                head.itemType(),
                head.trackInventory(),
                null,
                colours,
                null,
                options,
                null,
                null,
                head.lowStockLevel(),
                head.status()
        );

        ItemResponse item = matchedEntityId == null
                ? itemService.createItem(businessId, request, null)
                : replaceOptions(businessId, matchedEntityId, head, unitId, group, colours, options);

        Map<Integer, UUID> stock = postOptionStock(businessId, item, rows, job);

        return GroupCommitOutcome.of(
                matchedEntityId == null ? ImportRowStatus.CREATED : ImportRowStatus.UPDATED,
                item.id(),
                createdGroupIds(group),
                stock
        );
    }

    /**
     * Rewrites an existing item's options from the file.
     *
     * Wholesale, because that is the only way the catalogue offers — and it is
     * what the shop asked for by choosing to update. The preview says so before
     * they get here.
     */
    private ItemResponse replaceOptions(
            UUID businessId,
            UUID itemId,
            ItemImportRecord head,
            UUID unitId,
            ResolvedGroup group,
            List<ItemColorRequest> colours,
            List<ItemVariantRequest> options
    ) {
        return itemService.updateItem(
                businessId,
                itemId,
                new UpdateItemRequest(
                        group.id(),
                        unitId,
                        head.name(),
                        head.groupKey(),
                        null,
                        head.description(),
                        head.imageUrl(),
                        null,
                        head.badge(),
                        null,
                        head.price(),
                        head.itemType(),
                        head.trackInventory(),
                        null,
                        colours,
                        null,
                        options,
                        null,
                        null,
                        head.lowStockLevel(),
                        head.status()
                ),
                null
        );
    }

    /**
     * Whether this item is worth offering by colour at all.
     *
     * Two things have to hold. The colour must be an axis of its own — a watch
     * sold only in Rose Gold is not "sold by colour", it has one option that
     * happens to be a colour, and offering a swatch beside an identical option
     * name tells the shopper nothing. And every colour must be one we can
     * actually paint: a grey circle labelled "Rose Gold" is a broken promise,
     * and worse than no circle at all.
     *
     * Failing either, the colour becomes part of the option's name — "Small /
     * Navy" — which is honest, readable, and still counted separately.
     */
    private boolean sellsByColour(List<ItemImportRecord> records) {
        return records.stream().allMatch(record ->
                record.options().hasDistinctColourAxis()
                        && ColourNames.hexFor(record.options().colourValue()).isPresent());
    }

    /**
     * The colours this item comes in, declared once from the rows that name them.
     *
     * The catalogue refuses an option naming a colour the item does not
     * declare, so the declaration is assembled here rather than left to chance.
     * Each colour borrows the picture of the first option wearing it, which is
     * what a variant export's image column is usually showing anyway.
     */
    private List<ItemColorRequest> coloursOf(List<ItemImportRecord> records) {
        Map<String, ItemColorRequest> byValue = new LinkedHashMap<>();

        for (ItemImportRecord record : records) {
            String colour = record.options().colourValue();

            if (colour == null) {
                continue;
            }

            byValue.putIfAbsent(
                    colour.toLowerCase(Locale.ROOT),
                    new ItemColorRequest(
                            colour,
                            ColourNames.hexFor(colour).orElse(null),
                            record.imageUrl()));
        }

        return List.copyOf(byValue.values());
    }

    /**
     * @param asColours whether the colour is being offered as its own axis. When
     *                  it is not, it stays part of the option's name and the
     *                  option carries no colour — so nothing renders a swatch
     *                  for it.
     */
    private List<ItemVariantRequest> optionsOf(List<ItemImportRecord> records, boolean asColours) {
        List<ItemVariantRequest> options = new ArrayList<>();

        for (ItemImportRecord record : records) {
            String label = record.options().label();

            options.add(new ItemVariantRequest(
                    label,
                    record.sku(),
                    record.barcode(),
                    record.imageUrl(),
                    asColours ? record.options().optionName() : label,
                    asColours ? record.options().colourValue() : null,
                    record.price(),
                    Boolean.TRUE
            ));
        }

        return options;
    }

    /**
     * Posts each option's starting quantity against that option.
     *
     * The options come back from the catalogue with the ids they were given, so
     * each row's quantity is matched to its own shelf by the option's name —
     * the one thing the file and the saved item are certain to agree on.
     *
     * An option that already holds stock is left alone. An opening balance is
     * the first entry in a shelf's history and there can only be one, so a
     * shop re-importing its range to refresh prices must not have the same
     * quantities added on top of what it has been selling from all week. The
     * ledger refuses it anyway; catching it here keeps the item a clean update
     * rather than a failure.
     */
    private Map<Integer, UUID> postOptionStock(
            UUID businessId,
            ItemResponse item,
            List<OptionRow> rows,
            ImportJob job
    ) {
        if (!Boolean.TRUE.equals(item.trackInventory()) || item.variants() == null) {
            return Map.of();
        }

        Map<String, UUID> optionIds = new LinkedHashMap<>();

        for (ItemVariantResponse variant : item.variants()) {
            if (variant.name() != null) {
                optionIds.putIfAbsent(variant.name().toLowerCase(Locale.ROOT), variant.id());
            }
        }

        Map<Integer, UUID> posted = new LinkedHashMap<>();

        for (OptionRow row : rows) {
            ItemImportRecord record = row.record();

            if (!record.hasOpeningStock()) {
                continue;
            }

            UUID variantId = optionIds.get(record.options().label().toLowerCase(Locale.ROOT));

            if (variantId == null || alreadyCounted(businessId, item.id(), variantId)) {
                continue;
            }

            UUID entryId = openingStockPoster.post(
                    businessId,
                    item.id(),
                    variantId,
                    record.openingStock(),
                    record.costPrice(),
                    job.getId(),
                    job.getSourceFileName()
            );

            if (entryId != null) {
                posted.put(row.rowNumber(), entryId);
            }
        }

        return posted;
    }
}
