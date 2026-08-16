package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.OptionPresetRequest;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetResponse;

import java.util.List;
import java.util.UUID;

public interface OptionPresetService {

    List<OptionPresetResponse> findAllOptionPresets(UUID businessId);

    OptionPresetResponse createOptionPreset(UUID businessId, OptionPresetRequest request);

    OptionPresetResponse updateOptionPreset(UUID businessId, UUID presetId, OptionPresetRequest request);

    void deleteOptionPreset(UUID businessId, UUID presetId);
}
