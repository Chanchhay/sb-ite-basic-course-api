package kh.edu.istad.ite.features.catalog.mapper;

import kh.edu.istad.ite.features.catalog.dto.OptionPresetResponse;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetValueResponse;
import kh.edu.istad.ite.features.catalog.entity.OptionPreset;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OptionPresetMapper {

    public OptionPresetResponse toResponse(OptionPreset preset) {
        if (preset == null) {
            return null;
        }

        return new OptionPresetResponse(
                preset.getId(),
                preset.getName(),
                preset.getType(),
                preset.getRequired(),
                preset.getValues() == null
                        ? List.of()
                        : preset.getValues().stream()
                                .map(value -> new OptionPresetValueResponse(
                                        value.getValue(),
                                        value.getColorHex()
                                ))
                                .toList()
        );
    }
}
