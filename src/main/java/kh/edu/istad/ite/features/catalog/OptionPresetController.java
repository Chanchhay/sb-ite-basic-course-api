package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetRequest;
import kh.edu.istad.ite.features.catalog.dto.OptionPresetResponse;
import kh.edu.istad.ite.features.catalog.service.OptionPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/option-presets")
@RequiredArgsConstructor
public class OptionPresetController {

    private final OptionPresetService optionPresetService;

    @GetMapping
    public Page<OptionPresetResponse> findAllOptionPresets(
            @PathVariable UUID businessId,
            @PageableDefault (sort = "name", direction = Sort.Direction.ASC)Pageable pageable
            ) {
        return optionPresetService.findAllOptionPresets(businessId , pageable);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public OptionPresetResponse createOptionPreset(
            @PathVariable UUID businessId,
            @Valid @RequestBody OptionPresetRequest request
    ) {
        return optionPresetService.createOptionPreset(businessId, request);
    }

    @PutMapping("/{presetId}")
    public OptionPresetResponse updateOptionPreset(
            @PathVariable UUID businessId,
            @PathVariable UUID presetId,
            @Valid @RequestBody OptionPresetRequest request
    ) {
        return optionPresetService.updateOptionPreset(businessId, presetId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{presetId}")
    public void deleteOptionPreset(
            @PathVariable UUID businessId,
            @PathVariable UUID presetId
    ) {
        optionPresetService.deleteOptionPreset(businessId, presetId);
    }
}
