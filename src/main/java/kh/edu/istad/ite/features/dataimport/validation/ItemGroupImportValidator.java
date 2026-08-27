package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.ItemGroupImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ItemGroupImportValidator implements ImportRowValidator {

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.ITEM_GROUP;
    }

    @Override
    public RowVerdict validate(
            ImportRecord record,
            int rowNumber,
            ValidationContext context,
            MappingPlan plan
    ) {
        ItemGroupImportRecord group = (ItemGroupImportRecord) record;
        List<RowIssue> issues = new ArrayList<>();

        if (group.name() == null) {
            issues.add(RowIssue.error(
                    ImportField.NAME.name(),
                    "MISSING_NAME",
                    "A category needs a name."
            ));
            return RowVerdict.invalid(issues);
        }

        validateParent(group, context, issues);

        boolean unusable = issues.stream().anyMatch(RowIssue::isError);
        if (unusable) {
            return RowVerdict.invalid(issues);
        }

        Integer takenBy = context.rowThatTookGroupName(group.name());
        if (takenBy != null) {
            issues.add(RowIssue.warning(
                    ImportField.NAME.name(),
                    "DUPLICATE_IN_FILE",
                    "\"" + group.name() + "\" also appears on row " + takenBy + " of this file."
            ));
            return RowVerdict.duplicate(issues, null);
        }

        context.claimGroupName(group.name(), rowNumber);

        UUID existingId = context.findItemGroupId(group.name());
        if (existingId != null) {
            issues.add(RowIssue.warning(
                    ImportField.NAME.name(),
                    "ALREADY_EXISTS",
                    "You already have a category called \"" + group.name() + "\"."
            ));
            return RowVerdict.duplicate(issues, existingId);
        }

        context.planItemGroup(group.name());

        return RowVerdict.valid(issues);
    }

    /**
     * A parent has to be a category that exists, or one this file creates
     * before it gets here, and it may not itself be a sub-category.
     */
    private void validateParent(
            ItemGroupImportRecord group,
            ValidationContext context,
            List<RowIssue> issues
    ) {
        String parent = group.parentName();

        if (parent == null) {
            return;
        }

        if (parent.equalsIgnoreCase(group.name())) {
            issues.add(RowIssue.error(
                    ImportField.PARENT_GROUP.name(),
                    "PARENT_IS_SELF",
                    "A category cannot sit under itself."
            ));
            return;
        }

        if (!context.hasItemGroup(parent) && !context.isItemGroupPlanned(parent)) {
            issues.add(RowIssue.error(
                    ImportField.PARENT_GROUP.name(),
                    "UNKNOWN_PARENT",
                    "There is no category called \"" + parent + "\" to put this one under."
            ));
            return;
        }

        if (context.isSubGroup(parent)) {
            issues.add(RowIssue.error(
                    ImportField.PARENT_GROUP.name(),
                    "PARENT_TOO_DEEP",
                    "\"" + parent + "\" is already a sub-category. Categories go two levels deep."
            ));
        }
    }
}
