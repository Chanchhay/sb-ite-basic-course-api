package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.AddOnSetRequest;
import kh.edu.istad.ite.features.catalog.dto.AddOnSetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AddOnSetService {

    Page<AddOnSetResponse> findAllAddOnSets(UUID businessId, Pageable pageable);

    AddOnSetResponse createAddOnSet(UUID businessId, AddOnSetRequest request);

    AddOnSetResponse updateAddOnSet(UUID businessId, UUID setId, AddOnSetRequest request);

    void deleteAddOnSet(UUID businessId, UUID setId);
}
