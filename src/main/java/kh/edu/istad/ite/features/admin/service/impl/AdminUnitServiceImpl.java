package kh.edu.istad.ite.features.admin.service.impl;

import kh.edu.istad.ite.features.admin.dto.request.UnitUpsertRequest;
import kh.edu.istad.ite.features.admin.service.AdminAuditLogService;
import kh.edu.istad.ite.features.admin.service.AdminUnitService;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.mapper.UnitMapper;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUnitServiceImpl implements AdminUnitService {

    private static final int SLUG_MAX_LENGTH = 250;

    private final UnitRepository unitRepository;
    private final ItemRepository itemRepository;
    private final UnitMapper unitMapper;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @Transactional
    public UnitResponse createUnit(UnitUpsertRequest request) {
        String trimmedName = request.name().trim();

        Unit unit = new Unit();
        unit.setName(trimmedName);
        unit.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        unit.setSlug(generateUniqueSlug(trimmedName, null));

        Unit saved = unitRepository.save(unit);

        adminAuditLogService.record(
                AdminActionType.UNIT_CREATED,
                AuditTargetType.UNIT,
                saved.getId(),
                saved.getName(),
                null
        );

        return unitMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UnitResponse updateUnit(UUID unitId, UnitUpsertRequest request) {
        Unit unit = findUnit(unitId);
        String previousName = unit.getName();

        String trimmedName = request.name().trim();
        if (!trimmedName.equals(unit.getName())) {
            unit.setName(trimmedName);
            unit.setSlug(generateUniqueSlug(trimmedName, unit.getId()));
        }

        unit.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);

        Unit saved = unitRepository.save(unit);

        adminAuditLogService.recordStateChange(
                AdminActionType.UNIT_UPDATED,
                AuditTargetType.UNIT,
                saved.getId(),
                saved.getName(),
                previousName,
                saved.getName(),
                null
        );

        return unitMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitResponse getUnitById(UUID unitId) {
        return unitMapper.toResponse(findUnit(unitId));
    }

    @Override
    @Transactional
    public void deleteUnit(UUID unitId) {
        Unit unit = findUnit(unitId);

        if (itemRepository.existsByUnit_Id(unitId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a unit that is still assigned to items");
        }

        String deletedName = unit.getName();
        unitRepository.delete(unit);

        adminAuditLogService.record(
                AdminActionType.UNIT_DELETED,
                AuditTargetType.UNIT,
                unitId,
                deletedName,
                null
        );
    }

    private Unit findUnit(UUID unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit has not been found"));
    }

    private String generateUniqueSlug(String name, UUID excludedUnitId) {
        String baseSlug = toSlugBase(name);
        String candidate = baseSlug;
        int suffix = 1;

        while (slugExists(candidate, excludedUnitId)) {
            String suffixText = "-" + suffix;
            int baseMaxLength = SLUG_MAX_LENGTH - suffixText.length();
            candidate = baseSlug.substring(0, Math.min(baseSlug.length(), baseMaxLength)).replaceAll("-$", "") + suffixText;
            suffix++;
        }

        return candidate;
    }

    private boolean slugExists(String slug, UUID excludedUnitId) {
        if (excludedUnitId == null) {
            return unitRepository.existsBySlug(slug);
        }

        return unitRepository.existsBySlugAndIdNot(slug, excludedUnitId);
    }

    private String toSlugBase(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (!StringUtils.hasText(normalized)) {
            return "unit";
        }

        return normalized.length() > SLUG_MAX_LENGTH
                ? normalized.substring(0, SLUG_MAX_LENGTH).replaceAll("-$", "")
                : normalized;
    }
}
