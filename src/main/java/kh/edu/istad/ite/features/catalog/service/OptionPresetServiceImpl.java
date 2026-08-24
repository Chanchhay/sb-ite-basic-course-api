package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetRequest;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetResponse;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetValueRequest;
import kh.edu.istad.ite.features.catalog.entity.OptionPreset;
import kh.edu.istad.ite.features.catalog.entity.OptionPresetValue;
import kh.edu.istad.ite.features.catalog.mapper.OptionPresetMapper;
import kh.edu.istad.ite.features.catalog.repository.OptionPresetRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Presets a business saves so a list of choices is typed once.
 *
 * Deleting one is always allowed: applying a preset copies its values onto the
 * item, so nothing depends on it afterwards. That is the whole point of it
 * being a starting point rather than a link.
 */
@Service
@RequiredArgsConstructor
public class OptionPresetServiceImpl implements OptionPresetService {

    private final BusinessHelper businessHelper;
    private final OptionPresetRepository optionPresetRepository;
    private final OptionPresetMapper optionPresetMapper;


    @Override
    @Transactional(readOnly = true)
    public Page<OptionPresetResponse> findAllOptionPresets(UUID businessId, Pageable pageable) {

        businessHelper.findAccessibleBusiness(businessId);

        return optionPresetRepository.findByBusinessId(businessId, pageable)
                .map(optionPresetMapper::toResponse);
    }

    @Override
    @Transactional
    public OptionPresetResponse createOptionPreset(UUID businessId, OptionPresetRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        String name = TextHelper.trimRequired(request.name(), "Preset name cannot be empty");

        if (optionPresetRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preset with this name already exists");
        }

        OptionPreset preset = new OptionPreset();
        preset.setBusiness(business);
        preset.setName(name);
        preset.setType(request.type());
        preset.setRequired(request.required());
        preset.setValues(toValues(request.values()));

        try {
            return optionPresetMapper.toResponse(optionPresetRepository.saveAndFlush(preset));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preset already exists", e);
        }
    }

    @Override
    @Transactional
    public OptionPresetResponse updateOptionPreset(
            UUID businessId,
            UUID presetId,
            OptionPresetRequest request
    ) {
        businessHelper.findOwnedBusiness(businessId);
        OptionPreset preset = findPreset(presetId, businessId);
        String name = TextHelper.trimRequired(request.name(), "Preset name cannot be empty");

        if (!name.equals(preset.getName())
                && optionPresetRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, presetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preset with this name already exists");
        }

        preset.setName(name);
        preset.setType(request.type());
        preset.setRequired(request.required());
        preset.setValues(toValues(request.values()));

        try {
            return optionPresetMapper.toResponse(optionPresetRepository.saveAndFlush(preset));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preset already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteOptionPreset(UUID businessId, UUID presetId) {
        businessHelper.findOwnedBusiness(businessId);
        optionPresetRepository.delete(findPreset(presetId, businessId));
        optionPresetRepository.flush();
    }

    private OptionPreset findPreset(UUID presetId, UUID businessId) {
        return optionPresetRepository.findByIdAndBusinessId(presetId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset has not been found"));
    }

    /** Duplicates are rejected: two identical choices are a typo, not a list. */
    private List<OptionPresetValue> toValues(List<OptionPresetValueRequest> requests) {
        List<OptionPresetValue> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (OptionPresetValueRequest request : requests) {
            String text = TextHelper.trimRequired(request.value(), "A preset value cannot be empty");

            if (!seen.add(text.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Preset values must be unique: " + text
                );
            }

            OptionPresetValue value = new OptionPresetValue();
            value.setValue(text);
            value.setColorHex(TextHelper.trimToNull(request.colorHex()));
            value.setImageUrl(TextHelper.trimToNull(request.imageUrl()));
            values.add(value);
        }

        return values;
    }
}
