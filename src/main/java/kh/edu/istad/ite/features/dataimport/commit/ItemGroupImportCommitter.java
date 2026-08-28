package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemSubGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.service.ItemGroupService;
import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.ItemGroupImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ItemGroupImportCommitter implements ImportCommitter {

    private final ItemGroupService itemGroupService;
    private final ItemGroupRepository itemGroupRepository;

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.ITEM_GROUP;
    }

    @Override
    public CommitOutcome commit(
            ImportJob job,
            ImportRecord record,
            UUID matchedEntityId,
            MappingPlan plan
    ) {
        ItemGroupImportRecord group = (ItemGroupImportRecord) record;
        UUID businessId = job.getBusiness().getId();

        if (matchedEntityId != null) {
            if (plan.duplicateStrategy() != ImportDuplicateStrategy.UPDATE_EXISTING) {
                return CommitOutcome.skipped(matchedEntityId);
            }

            /*
             * Only the note is rewritten. The name is what matched this row to
             * this category in the first place, and moving a category under a
             * different parent would take every item in it along — neither is
             * something a re-import should do behind the shop's back.
             */
            itemGroupService.updateItemGroup(
                    businessId,
                    matchedEntityId,
                    new UpdateItemGroupRequest(null, group.note(), null)
            );

            return CommitOutcome.updated(matchedEntityId, null, java.util.List.of());
        }

        UUID parentId = null;

        if (group.parentName() != null) {
            parentId = itemGroupRepository
                    .findFirstByBusinessIdAndNameIgnoreCase(businessId, group.parentName())
                    .map(ItemGroup::getId)
                    .orElse(null);

            /*
             * Checking passed this row because the parent either existed or was
             * due to be created earlier in the same file. If it is still not
             * here, that earlier row failed — and filing this category at the
             * top level instead would quietly flatten a hierarchy the shop
             * asked for. Better to fail the row and say why.
             */
            if (parentId == null) {
                return CommitOutcome.failed(
                        "The category \"" + group.parentName() + "\" was not created, so \""
                                + group.name() + "\" could not be put under it."
                );
            }
        }

        ItemSubGroupResponse created = itemGroupService.createItemGroup(
                businessId,
                new CreateItemGroupRequest(group.name(), group.note(), parentId)
        );

        return CommitOutcome.created(created.id(), null, java.util.List.of(created.id()));
    }
}
