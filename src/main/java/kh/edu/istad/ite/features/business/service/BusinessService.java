package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessRequest;

import java.util.UUID;

public interface BusinessService {
    BusinessResponse createBusiness(CreateBusinessRequest request);

    BusinessResponse getMyBusiness();

    BusinessResponse getBusiness(UUID businessId);

    BusinessResponse updateBusiness(UUID businessId, UpdateBusinessRequest request);

    BusinessResponse deleteBusiness(UUID businessId);
}
