package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
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
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
                        item.compareAtPrice(),
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

        return CommitOutcome.created(created.id(), stockEntryId, group.created());
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
                        item.compareAtPrice(),
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

        return CommitOutcome.updated(updated.id(), stockEntryId, group.created());
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

        if (existing != null) {
            return new ResolvedGroup(existing.getId(), false);
        }

        UUID createdId = itemGroupService
                .createItemGroup(businessId, new CreateItemGroupRequest(item.itemGroupName(), null, null))
                .id();

        return new ResolvedGroup(createdId, true);
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

    /** A category id, and whether this import is what brought it into being. */
    private record ResolvedGroup(UUID id, boolean created) {
    }
}
