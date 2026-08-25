package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.OptionPresetRequest;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OptionPresetService {

    Page<OptionPresetResponse> findAllOptionPresets(UUID businessId, Pageable pageable);

    OptionPresetResponse createOptionPreset(UUID businessId, OptionPresetRequest request);

    OptionPresetResponse updateOptionPreset(UUID businessId, UUID presetId, OptionPresetRequest request);

    void deleteOptionPreset(UUID businessId, UUID presetId);
}
