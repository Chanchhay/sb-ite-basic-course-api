package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.BusinessUnitRequest;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BusinessUnitService {

    /** Platform units plus this business's own, name-ordered. */
    Page<UnitResponse> findSelectableUnits(UUID businessId , Pageable pageable);

    UnitResponse createUnit(UUID businessId, BusinessUnitRequest request);

    UnitResponse updateUnit(UUID businessId, UUID unitId, BusinessUnitRequest request);

    void deleteUnit(UUID businessId, UUID unitId);
}
