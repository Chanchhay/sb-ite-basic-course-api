package kh.edu.istad.ite.features.order.service;

import kh.edu.istad.ite.features.catalog.entity.ModifierGroup;
import kh.edu.istad.ite.features.catalog.entity.ModifierOption;
import kh.edu.istad.ite.features.catalog.repository.ModifierGroupRepository;
import kh.edu.istad.ite.features.order.dto.ModifierSelectionRequest;
import kh.edu.istad.ite.features.order.entity.SelectedModifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class OrderModifierResolver {

    private final ModifierGroupRepository modifierGroupRepository;

    public List<SelectedModifier> resolve(
            UUID businessId, UUID itemId, List<ModifierSelectionRequest> selections) {

        List<ModifierSelectionRequest> requested = selections == null ? List.of() : selections;

        List<ModifierGroup> groups = modifierGroupRepository
                .findAllByItemIdAndBusinessIdOrderBySortOrderAsc(itemId, businessId);

        Map<UUID, ModifierOption> optionsById = new HashMap<>();
        for (ModifierGroup group : groups) {
            for (ModifierOption option : group.getOptions()) {
                optionsById.put(option.getId(), option);
            }
        }

        List<SelectedModifier> resolved = new ArrayList<>();
        Map<UUID, Integer> countByGroup = new HashMap<>();

        for (ModifierSelectionRequest selection : requested) {
            ModifierOption option = optionsById.get(selection.optionId());
            if (option == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Modifier option does not belong to this item: " + selection.optionId());
            }

            int quantity = selection.quantity() == null ? 1 : selection.quantity();
            resolved.add(new SelectedModifier(
                    option.getGroup().getName(),
                    option.getName(),
                    option.getPrice(),
                    quantity));

            countByGroup.merge(option.getGroup().getId(), 1, Integer::sum);
        }

        for (ModifierGroup group : groups) {
            int count = countByGroup.getOrDefault(group.getId(), 0);

            if (count < group.getMinSelect()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Group '" + group.getName() + "' requires at least "
                                + group.getMinSelect() + " selection(s)");
            }
            if (group.getMaxSelect() != null && count > group.getMaxSelect()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Group '" + group.getName() + "' allows at most "
                                + group.getMaxSelect() + " selection(s)");
            }
        }

        return resolved;
    }
}