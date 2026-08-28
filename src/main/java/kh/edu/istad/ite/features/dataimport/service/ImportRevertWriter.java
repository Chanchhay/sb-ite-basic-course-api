package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.service.ItemGroupService;
import kh.edu.istad.ite.features.catalog.service.ItemService;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Takes one imported item back out, in a transaction of its own.
 *
 * Each item stands alone deliberately. An undo runs over a catalogue that has
 * been open for business since the import, so some of what it wants to remove
 * will have been sold and cannot go — and one refusal must not roll back the
 * hundred deletions that worked.
 *
 * Note what is *not* here: nothing catches an exception inside the transaction
 * that raised it. A delete that fails marks its transaction rollback-only, and
 * a catch block inside that transaction is powerless — the commit at the end
 * throws anyway, and everything the method did goes with it. So the attempt and
 * the record of the attempt are separate transactions, and the caller, which is
 * in neither, is what decides between them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportRevertWriter {

    private final ItemService itemService;
    private final ItemGroupService itemGroupService;
    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final ImportRowRepository importRowRepository;

    /**
     * Deletes one item the import created. Throws if it cannot.
     *
     * The ledger it counted goes with it — {@code deleteItem} clears the stock
     * entries first — so an item whose only history is the opening balance this
     * import posted comes out cleanly. One with orders against it does not, and
     * that refusal is the point rather than a problem.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteImportedItem(UUID businessId, UUID itemId) {
        itemService.deleteItem(businessId, itemId);
    }

    /** Deletes a category the import invented. Throws if anything still uses it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteImportedItemGroup(UUID businessId, UUID itemGroupId) {
        itemGroupService.deleteItemGroup(businessId, itemGroupId);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean itemIsGone(UUID businessId, UUID itemId) {
        return itemRepository.findByIdAndBusinessId(itemId, businessId).isEmpty();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean itemGroupIsGone(UUID businessId, UUID itemGroupId) {
        return itemGroupRepository.findByIdAndBusinessId(itemGroupId, businessId).isEmpty();
    }

    /**
     * Marks the rows whose item has gone.
     *
     * Its own transaction, run only after a deletion that actually succeeded,
     * so a row can never claim an item was reverted that is still on the shelf.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReverted(List<UUID> rowIds) {
        List<ImportRow> rows = importRowRepository.findAllById(rowIds);

        for (ImportRow row : rows) {
            row.setStatus(ImportRowStatus.REVERTED);
            row.setCommittedStockEntryId(null);
        }

        importRowRepository.saveAll(rows);
    }
}
