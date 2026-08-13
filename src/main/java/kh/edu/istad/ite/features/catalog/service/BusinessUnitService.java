package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.BusinessUnitRequest;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;

import java.util.List;
import java.util.UUID;

public interface BusinessUnitService {

    /** Platform units plus this business's own, name-ordered. */
    List<UnitResponse> findSelectableUnits(UUID businessId);

    UnitResponse createUnit(UUID businessId, BusinessUnitRequest request);

    UnitResponse updateUnit(UUID businessId, UUID unitId, BusinessUnitRequest request);

    void deleteUnit(UUID businessId, UUID unitId);
}
