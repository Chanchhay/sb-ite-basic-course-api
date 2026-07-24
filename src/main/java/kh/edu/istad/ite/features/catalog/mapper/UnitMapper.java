package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import org.springframework.stereotype.Component;

@Component
public class UnitMapper {

    public UnitResponse toResponse(Unit unit) {
        if (unit == null) {
            return null;
        }

        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getSlug(),
                unit.getNote(),
                unit.getCreatedDate(),
                unit.getLastModifiedDate()
        );
    }
}
