package kh.edu.istad.ite.features.customer.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.customer.dto.CreateMembershipTypeRequest;
import kh.edu.istad.ite.features.customer.dto.MembershipTypeResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateMembershipTypeRequest;
import kh.edu.istad.ite.features.customer.entity.MembershipType;
import kh.edu.istad.ite.features.customer.mapper.MembershipTypeMapper;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.repository.MembershipTypeRepository;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipTypeServiceImpl implements MembershipTypeService {

    private final BusinessHelper businessHelper;
    private final MembershipTypeRepository membershipTypeRepository;
    private final DiscountRepository discountRepository;
    private final CustomerRepository customerRepository;
    private final MembershipTypeMapper membershipTypeMapper;

    @Override
    @Transactional
    public MembershipTypeResponse createMembershipType(UUID businessId, CreateMembershipTypeRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);

        String typeName = TextHelper.trimRequired(request.typeName(), "Membership type name cannot be empty");
        if (membershipTypeRepository.existsByBusinessIdAndTypeNameIgnoreCase(businessId, typeName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership type with this name already exists");
        }

        MembershipType membershipType = new MembershipType();
        membershipType.setBusiness(business);
        membershipType.setTypeName(typeName);
        membershipType.setRemark(TextHelper.trimToNull(request.remark()));
        membershipType.setDiscount(findDiscountOrNull(request.discountId(), businessId));
        membershipType.setStatus(RecordStatus.ACTIVE);

        try {
            return membershipTypeMapper.toResponse(membershipTypeRepository.saveAndFlush(membershipType));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership type already exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MembershipTypeResponse> findAllMembershipTypes(UUID businessId, Pageable pageable) {
        businessHelper.findOwnedBusiness(businessId);

        return membershipTypeRepository.findAllByBusinessId(businessId, pageable)
                .map(membershipTypeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipTypeResponse findMembershipTypeById(UUID businessId, UUID membershipTypeId) {
        businessHelper.findOwnedBusiness(businessId);
        return membershipTypeMapper.toResponse(findMembershipType(membershipTypeId, businessId));
    }

    @Override
    @Transactional
    public MembershipTypeResponse updateMembershipType(
            UUID businessId,
            UUID membershipTypeId,
            UpdateMembershipTypeRequest request
    ) {
        businessHelper.findOwnedBusiness(businessId);
        MembershipType membershipType = findMembershipType(membershipTypeId, businessId);

        if (request.typeName() != null) {
            String typeName = TextHelper.trimRequired(request.typeName(), "Membership type name cannot be empty");
            if (!typeName.equals(membershipType.getTypeName())) {
                if (membershipTypeRepository.existsByBusinessIdAndTypeNameIgnoreCaseAndIdNot(businessId, typeName, membershipTypeId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership type with this name already exists");
                }
                membershipType.setTypeName(typeName);
            }
        }
        if (request.remark() != null) {
            membershipType.setRemark(TextHelper.trimToNull(request.remark()));
        }
        if (request.discountId() != null) {
            membershipType.setDiscount(findDiscountOrNull(request.discountId(), businessId));
        }
        if (request.status() != null) {
            membershipType.setStatus(RecordStatus.valueOf(request.status()));
        }

        try {
            return membershipTypeMapper.toResponse(membershipTypeRepository.saveAndFlush(membershipType));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership type already exists", e);
        }
    }

    @Override
    @Transactional
    public MembershipTypeResponse activateMembershipType(UUID businessId, UUID membershipTypeId) {
        businessHelper.findOwnedBusiness(businessId);
        MembershipType membershipType = findMembershipType(membershipTypeId, businessId);

        membershipType.setStatus(RecordStatus.ACTIVE);
        return membershipTypeMapper.toResponse(membershipTypeRepository.saveAndFlush(membershipType));
    }

    @Override
    @Transactional
    public MembershipTypeResponse deactivateMembershipType(UUID businessId, UUID membershipTypeId) {
        businessHelper.findOwnedBusiness(businessId);
        MembershipType membershipType = findMembershipType(membershipTypeId, businessId);

        membershipType.setStatus(RecordStatus.INACTIVE);
        return membershipTypeMapper.toResponse(membershipTypeRepository.saveAndFlush(membershipType));
    }

    @Override
    @Transactional
    public void deleteMembershipType(UUID businessId, UUID membershipTypeId) {
        businessHelper.findOwnedBusiness(businessId);
        MembershipType membershipType = findMembershipType(membershipTypeId, businessId);

        if (customerRepository.existsByMembershipType_Id(membershipTypeId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete membership type that is assigned to customers"
            );
        }

        membershipTypeRepository.delete(membershipType);
        membershipTypeRepository.flush();
    }

    private Discount findDiscountOrNull(UUID discountId, UUID businessId) {
        if (discountId == null) {
            return null;
        }

        return discountRepository.findByIdAndBusinessId(discountId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount has not been found"));
    }

    private MembershipType findMembershipType(UUID membershipTypeId, UUID businessId) {
        return membershipTypeRepository.findByIdAndBusinessId(membershipTypeId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership type has not been found"));
    }
}
