package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.AddOnResponse;
import kh.edu.istad.ite.features.catalog.dto.CreateAddOnRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateAddOnRequest;

import java.util.List;
import java.util.UUID;

public interface AddOnService {

    AddOnResponse createAddOn(UUID businessId, CreateAddOnRequest request);

    List<AddOnResponse> findAllAddOns(UUID businessId);

    AddOnResponse updateAddOn(UUID businessId, UUID addOnId, UpdateAddOnRequest request);

    void deleteAddOn(UUID businessId, UUID addOnId);
}
