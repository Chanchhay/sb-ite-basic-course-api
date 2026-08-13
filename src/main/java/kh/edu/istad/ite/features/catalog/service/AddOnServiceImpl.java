package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.AddOnResponse;
import kh.edu.istad.ite.features.catalog.dto.CreateAddOnRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateAddOnRequest;
import kh.edu.istad.ite.features.catalog.dto.AddOnUomConversionRequest;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.AddOnUomConversion;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.mapper.AddOnMapper;
import kh.edu.istad.ite.features.catalog.repository.AddOnRepository;
import kh.edu.istad.ite.features.catalog.repository.AddOnSetRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddOnServiceImpl implements AddOnService {

    private static final int SLUG_MAX_LENGTH = 200;
    private static final String SLUG_FALLBACK = "add-on";

    private final BusinessHelper businessHelper;
    private final AddOnRepository addOnRepository;
    private final UnitRepository unitRepository;
    private final ItemRepository itemRepository;
    private final AddOnSetRepository addOnSetRepository;
    private final AddOnMapper addOnMapper;

    @Override
    @Transactional
    public AddOnResponse createAddOn(UUID businessId, CreateAddOnRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        AddOn addOn = new AddOn();
        addOn.setBusiness(business);
        addOn.setName(TextHelper.trimRequired(request.name(), "Add-on name cannot be empty"));
        addOn.setSlug(generateUniqueSlug(addOn.getName(), businessId));
        addOn.setBaseUnit(findUnit(request.baseUnitId()));
        addOn.setUsePerOrder(request.usePerOrder());
        addOn.setPrice(request.price());
        addOn.setNote(TextHelper.trimToNull(request.note()));
        replaceConversions(addOn, request.uomConversions());

        if (addOnRepository.existsByBusinessIdAndNameIgnoreCase(businessId, addOn.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add-on with this name already exists");
        }

        try {
            return addOnMapper.toResponse(addOnRepository.saveAndFlush(addOn));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add-on already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddOnResponse> findAllAddOns(UUID businessId) {
        businessHelper.findAccessibleBusiness(businessId);

        return addOnRepository.findByBusinessIdOrderByNameAsc(businessId)
                .stream()
                .map(addOnMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddOnResponse updateAddOn(UUID businessId, UUID addOnId, UpdateAddOnRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        AddOn addOn = findAddOn(addOnId, businessId);

        if (request.name() != null) {
            String name = TextHelper.trimRequired(request.name(), "Add-on name cannot be empty");

            if (!name.equals(addOn.getName())) {
                if (addOnRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, addOnId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Add-on with this name already exists");
                }
                addOn.setName(name);
                addOn.setSlug(generateUniqueSlug(name, businessId, addOnId));
            }
        }

        if (request.baseUnitId() != null) {
            addOn.setBaseUnit(findUnit(request.baseUnitId()));
        }

        if (request.usePerOrder() != null) {
            addOn.setUsePerOrder(request.usePerOrder());
        }

        if (request.price() != null) {
            addOn.setPrice(request.price());
        }

        if (request.note() != null) {
            addOn.setNote(TextHelper.trimToNull(request.note()));
        }

        if (request.uomConversions() != null) {
            replaceConversions(addOn, request.uomConversions());
        }

        try {
            return addOnMapper.toResponse(addOnRepository.saveAndFlush(addOn));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add-on already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteAddOn(UUID businessId, UUID addOnId) {
        businessHelper.findOwnedBusiness(businessId);
        AddOn addOn = findAddOn(addOnId, businessId);

        // Deleting one that items still offer would silently drop the extra
        // from every one of them, so it is detached first, deliberately.
        if (itemRepository.existsByBusinessIdAndAddOnsId(businessId, addOnId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete add-on that is attached to items"
            );
        }

        if (addOnSetRepository.existsByBusinessIdAndAddOnsId(businessId, addOnId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete add-on that belongs to a set"
            );
        }

        addOnRepository.delete(addOn);
        addOnRepository.flush();
    }

    /**
     * Rewrites the add-on's conversions.
     *
     * The base unit is rejected rather than ignored: "1 g = 5 g" is not a
     * conversion anyone meant to type, and storing it would make every amount
     * on the add-on ambiguous.
     */
    private void replaceConversions(AddOn addOn, List<AddOnUomConversionRequest> requests) {
        addOn.getUomConversions().clear();

        if (requests == null || requests.isEmpty()) {
            return;
        }

        UUID baseUnitId = addOn.getBaseUnit() == null ? null : addOn.getBaseUnit().getId();
        Set<UUID> seen = new HashSet<>();

        for (AddOnUomConversionRequest request : requests) {
            UUID unitId = request.unitId();

            if (unitId.equals(baseUnitId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A conversion cannot be against the base unit"
                );
            }
            if (!seen.add(unitId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each unit can be converted only once"
                );
            }

            AddOnUomConversion conversion = new AddOnUomConversion();
            conversion.setAddOn(addOn);
            conversion.setUnit(findUnit(unitId));
            conversion.setFactor(request.factor());
            addOn.getUomConversions().add(conversion);
        }
    }

    private AddOn findAddOn(UUID addOnId, UUID businessId) {
        return addOnRepository.findByIdAndBusinessId(addOnId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Add-on has not been found"));
    }

    private Unit findUnit(UUID unitId) {
        if (unitId == null) {
            return null;
        }

        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit has not been found"));
    }

    private String generateUniqueSlug(String name, UUID businessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> addOnRepository.existsByBusinessIdAndSlugIgnoreCase(businessId, slug)
        );
    }

    private String generateUniqueSlug(String name, UUID businessId, UUID excludedAddOnId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> addOnRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(businessId, slug, excludedAddOnId)
        );
    }
}
