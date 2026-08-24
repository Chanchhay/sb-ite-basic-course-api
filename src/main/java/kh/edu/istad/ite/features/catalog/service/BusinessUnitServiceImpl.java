package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.BusinessUnitRequest;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.mapper.UnitMapper;
import kh.edu.istad.ite.features.catalog.repository.AddOnRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemUomConversionRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Units a business owns.
 *
 * Platform units are readable here and editable nowhere: they are shared by
 * every business, so one owner renaming "Gram" would rewrite it for all of
 * them.
 */
@Service
@RequiredArgsConstructor
public class BusinessUnitServiceImpl implements BusinessUnitService {

    private static final int SLUG_MAX_LENGTH = 250;
    private static final String SLUG_FALLBACK = "unit";

    private final BusinessHelper businessHelper;
    private final UnitRepository unitRepository;
    private final ItemRepository itemRepository;
    private final AddOnRepository addOnRepository;
    private final ItemUomConversionRepository itemUomConversionRepository;
    private final UnitMapper unitMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<UnitResponse> findSelectableUnits(UUID businessId, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);

        return unitRepository.findByBusinessIsNullOrBusinessId(businessId, pageable)
                .map(unitMapper::toResponse);
    }

    @Override
    @Transactional
    public UnitResponse createUnit(UUID businessId, BusinessUnitRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        String name = TextHelper.trimRequired(request.name(), "Unit name cannot be empty");

        if (unitRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit with this name already exists");
        }

        Unit unit = new Unit();
        unit.setBusiness(business);
        unit.setName(name);
        unit.setSlug(generateUniqueSlug(name, businessId));
        unit.setSymbol(TextHelper.trimRequired(request.symbol(), "Unit symbol cannot be empty"));
        unit.setCategory(request.category());
        unit.setNote(TextHelper.trimToNull(request.note()));

        try {
            return unitMapper.toResponse(unitRepository.saveAndFlush(unit));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit already exists", e);
        }
    }

    @Override
    @Transactional
    public UnitResponse updateUnit(UUID businessId, UUID unitId, BusinessUnitRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        Unit unit = findOwnUnit(unitId, businessId);
        String name = TextHelper.trimRequired(request.name(), "Unit name cannot be empty");

        if (!name.equals(unit.getName())) {
            if (unitRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, unitId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit with this name already exists");
            }
            unit.setName(name);
            unit.setSlug(generateUniqueSlug(name, businessId, unitId));
        }

        unit.setSymbol(TextHelper.trimRequired(request.symbol(), "Unit symbol cannot be empty"));
        unit.setCategory(request.category());
        unit.setNote(TextHelper.trimToNull(request.note()));

        try {
            return unitMapper.toResponse(unitRepository.saveAndFlush(unit));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteUnit(UUID businessId, UUID unitId) {
        businessHelper.findOwnedBusiness(businessId);
        Unit unit = findOwnUnit(unitId, businessId);

        // Anything still measured in it would be left without a unit, so the
        // three places that can hold one are checked before it goes.
        if (itemRepository.existsByUnit_Id(unitId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete a unit that is still assigned to items"
            );
        }
        if (addOnRepository.existsByBaseUnitId(unitId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete a unit that is still assigned to add-ons"
            );
        }
        if (itemUomConversionRepository.existsByUnitId(unitId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete a unit that item conversions still use"
            );
        }

        unitRepository.delete(unit);
        unitRepository.flush();
    }

    /** Platform units never resolve here, which is what makes them read-only. */
    private Unit findOwnUnit(UUID unitId, UUID businessId) {
        return unitRepository.findByIdAndBusinessId(unitId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unit has not been found"
                ));
    }

    private String generateUniqueSlug(String name, UUID businessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> unitRepository.existsByBusinessIdAndSlugIgnoreCase(businessId, slug)
        );
    }

    private String generateUniqueSlug(String name, UUID businessId, UUID excludedUnitId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> unitRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(businessId, slug, excludedUnitId)
        );
    }
}
