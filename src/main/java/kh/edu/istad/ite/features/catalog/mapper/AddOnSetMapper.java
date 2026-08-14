package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.AddOnSetResponse;
import kh.edu.istad.ite.features.catalog.entity.AddOnSet;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddOnSetMapper {

    private final AddOnMapper addOnMapper;

    public AddOnSetMapper(AddOnMapper addOnMapper) {
        this.addOnMapper = addOnMapper;
    }

    public AddOnSetResponse toResponse(AddOnSet set) {
        if (set == null) {
            return null;
        }

        return new AddOnSetResponse(
                set.getId(),
                set.getName(),
                set.getRule(),
                set.getMaxChoices(),
                set.getRequired(),
                set.getAddOns() == null
                        ? List.of()
                        : set.getAddOns().stream().map(addOnMapper::toResponse).toList()
        );
    }
}
