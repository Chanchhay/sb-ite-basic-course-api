package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.request.UnitUpsertRequest;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;

import java.util.UUID;

public interface AdminUnitService {

    UnitResponse createUnit(UnitUpsertRequest request);

    UnitResponse updateUnit(UUID unitId, UnitUpsertRequest request);

    UnitResponse getUnitById(UUID unitId);

    void deleteUnit(UUID unitId);
}
