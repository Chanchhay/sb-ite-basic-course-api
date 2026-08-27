package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.dto.AddOnSetRequest;
import kh.edu.istad.ite.features.catalog.dto.AddOnSetResponse;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.AddOnSet;
import kh.edu.istad.ite.features.catalog.mapper.AddOnSetMapper;
import kh.edu.istad.ite.features.catalog.repository.AddOnRepository;
import kh.edu.istad.ite.features.catalog.repository.AddOnSetRepository;
import kh.edu.istad.ite.shared.enums.AddOnSelectionRule;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddOnSetServiceImpl implements AddOnSetService {

    private final BusinessHelper businessHelper;
    private final AddOnSetRepository addOnSetRepository;
    private final AddOnRepository addOnRepository;
    private final AddOnSetMapper addOnSetMapper;


    @Override
    @Transactional(readOnly = true)
    public Page<AddOnSetResponse> findAllAddOnSets(UUID businessId, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);

        return addOnSetRepository.findByBusinessId(businessId, pageable)
                .map(addOnSetMapper::toResponse);
    }

    @Override
    @Transactional
    public AddOnSetResponse createAddOnSet(UUID businessId, AddOnSetRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        String name = TextHelper.trimRequired(request.name(), "Set name cannot be empty");

        if (addOnSetRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set with this name already exists");
        }

        AddOnSet set = new AddOnSet();
        set.setBusiness(business);
        set.setName(name);
        applyRule(set, request);
        set.setAddOns(resolveAddOns(businessId, request.addOnIds()));

        try {
            return addOnSetMapper.toResponse(addOnSetRepository.saveAndFlush(set));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set already exists", e);
        }
    }

    @Override
    @Transactional
    public AddOnSetResponse updateAddOnSet(UUID businessId, UUID setId, AddOnSetRequest request) {
        businessHelper.findOwnedBusiness(businessId);
        AddOnSet set = findSet(setId, businessId);
        String name = TextHelper.trimRequired(request.name(), "Set name cannot be empty");

        if (!name.equals(set.getName())
                && addOnSetRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, setId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set with this name already exists");
        }

        set.setName(name);
        applyRule(set, request);
        set.setAddOns(resolveAddOns(businessId, request.addOnIds()));

        try {
            return addOnSetMapper.toResponse(addOnSetRepository.saveAndFlush(set));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set already exists", e);
        }
    }

    @Override
    @Transactional
    public void deleteAddOnSet(UUID businessId, UUID setId) {
        businessHelper.findOwnedBusiness(businessId);
        // Only the grouping goes; the add-ons themselves stay in the library.
        addOnSetRepository.delete(findSet(setId, businessId));
        addOnSetRepository.flush();
    }

    /**
     * A ceiling above what the set holds is not a rule, it is a typo — "pick up
     * to 5 of these 3" tells a customer nothing.
     */
    private void applyRule(AddOnSet set, AddOnSetRequest request) {
        set.setRule(request.rule());
        set.setRequired(request.required());

        if (request.rule() == AddOnSelectionRule.UP_TO) {
            if (request.maxChoices() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "maxChoices is required when the rule is UP_TO"
                );
            }
            if (request.maxChoices() > request.addOnIds().size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "maxChoices cannot be more than the number of add-ons in the set"
                );
            }
        }

        set.setMaxChoices(request.maxChoices());
    }

    private LinkedHashSet<AddOn> resolveAddOns(UUID businessId, List<UUID> addOnIds) {
        List<UUID> wanted = addOnIds.stream().filter(Objects::nonNull).distinct().toList();
        List<AddOn> found = addOnRepository.findByBusinessIdAndIdIn(businessId, wanted);

        if (found.size() != wanted.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Add-on has not been found");
        }

        return new LinkedHashSet<>(found);
    }

    private AddOnSet findSet(UUID setId, UUID businessId) {
        return addOnSetRepository.findByIdAndBusinessId(setId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Set has not been found"));
    }
}
