package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.AddOnSetRequest;
import kh.edu.istad.ite.features.catalog.dto.AddOnSetResponse;

import java.util.List;
import java.util.UUID;

public interface AddOnSetService {

    List<AddOnSetResponse> findAllAddOnSets(UUID businessId);

    AddOnSetResponse createAddOnSet(UUID businessId, AddOnSetRequest request);

    AddOnSetResponse updateAddOnSet(UUID businessId, UUID setId, AddOnSetRequest request);

    void deleteAddOnSet(UUID businessId, UUID setId);
}
