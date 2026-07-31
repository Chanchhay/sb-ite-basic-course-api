package kh.edu.istad.ite.features.customer.service;

import kh.edu.istad.ite.features.customer.dto.CreateMembershipTypeRequest;
import kh.edu.istad.ite.features.customer.dto.MembershipTypeResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateMembershipTypeRequest;

import java.util.List;
import java.util.UUID;

public interface MembershipTypeService {

    MembershipTypeResponse createMembershipType(UUID businessId, CreateMembershipTypeRequest request);

    List<MembershipTypeResponse> findAllMembershipTypes(UUID businessId);

    MembershipTypeResponse findMembershipTypeById(UUID businessId, UUID membershipTypeId);

    MembershipTypeResponse updateMembershipType(UUID businessId, UUID membershipTypeId, UpdateMembershipTypeRequest request);

    MembershipTypeResponse activateMembershipType(UUID businessId, UUID membershipTypeId);

    MembershipTypeResponse deactivateMembershipType(UUID businessId, UUID membershipTypeId);

    void deleteMembershipType(UUID businessId, UUID membershipTypeId);
}
