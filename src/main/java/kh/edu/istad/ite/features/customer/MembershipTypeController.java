package kh.edu.istad.ite.features.customer;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.customer.dto.CreateMembershipTypeRequest;
import kh.edu.istad.ite.features.customer.dto.MembershipTypeResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateMembershipTypeRequest;
import kh.edu.istad.ite.features.customer.service.MembershipTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/membership-types")
@RequiredArgsConstructor
public class MembershipTypeController {

    private final MembershipTypeService membershipTypeService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public MembershipTypeResponse createMembershipType(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateMembershipTypeRequest request
    ) {
        return membershipTypeService.createMembershipType(businessId, request);
    }

    @GetMapping
    public Page<MembershipTypeResponse> findAllMembershipTypes(
            @PathVariable UUID businessId,
            @PageableDefault(sort = "typeName", direction = Sort.Direction.ASC)Pageable pageable

            ) {
        return membershipTypeService.findAllMembershipTypes(businessId , pageable);
    }

    @GetMapping("/{membershipTypeId}")
    public MembershipTypeResponse findMembershipTypeById(
            @PathVariable UUID businessId,
            @PathVariable UUID membershipTypeId
    ) {
        return membershipTypeService.findMembershipTypeById(businessId, membershipTypeId);
    }

    @PutMapping("/{membershipTypeId}")
    public MembershipTypeResponse updateMembershipType(
            @PathVariable UUID businessId,
            @PathVariable UUID membershipTypeId,
            @Valid @RequestBody UpdateMembershipTypeRequest request
    ) {
        return membershipTypeService.updateMembershipType(businessId, membershipTypeId, request);
    }

    @PatchMapping("/{membershipTypeId}/activate")
    public MembershipTypeResponse activateMembershipType(
            @PathVariable UUID businessId,
            @PathVariable UUID membershipTypeId
    ) {
        return membershipTypeService.activateMembershipType(businessId, membershipTypeId);
    }

    @PatchMapping("/{membershipTypeId}/deactivate")
    public MembershipTypeResponse deactivateMembershipType(
            @PathVariable UUID businessId,
            @PathVariable UUID membershipTypeId
    ) {
        return membershipTypeService.deactivateMembershipType(businessId, membershipTypeId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{membershipTypeId}")
    public void deleteMembershipType(
            @PathVariable UUID businessId,
            @PathVariable UUID membershipTypeId
    ) {
        membershipTypeService.deleteMembershipType(businessId, membershipTypeId);
    }
}
