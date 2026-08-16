package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.AddOnResponse;
import kh.edu.istad.ite.features.catalog.dto.AddOnUomConversionResponse;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddOnMapper {

    private final UnitMapper unitMapper;

    public AddOnMapper(UnitMapper unitMapper) {
        this.unitMapper = unitMapper;
    }

    public AddOnResponse toResponse(AddOn addOn) {
        if (addOn == null) {
            return null;
        }

        return new AddOnResponse(
                addOn.getId(),
                addOn.getName(),
                addOn.getSlug(),
                addOn.getBaseUnit() == null ? null : unitMapper.toResponse(addOn.getBaseUnit()),
                addOn.getUsePerOrder(),
                addOn.getPrice(),
                addOn.getUomConversions() == null
                        ? List.of()
                        : addOn.getUomConversions().stream()
                                .map(conversion -> new AddOnUomConversionResponse(
                                        conversion.getId(),
                                        unitMapper.toResponse(conversion.getUnit()),
                                        conversion.getFactor()
                                ))
                                .toList(),
                null,
                addOn.getNote()
        );
    }

    /** The same add-on, read through the item that offers it. */
    public AddOnResponse toResponse(kh.edu.istad.ite.features.catalog.entity.ItemAddOn link) {
        AddOnResponse addOn = toResponse(link.getAddOn());

        if (addOn == null) {
            return null;
        }

        return new AddOnResponse(
                addOn.id(),
                addOn.name(),
                addOn.slug(),
                addOn.baseUnit(),
                addOn.usePerOrder(),
                addOn.price(),
                addOn.uomConversions(),
                link.isAvailable(),
                addOn.note()
        );
    }
}
