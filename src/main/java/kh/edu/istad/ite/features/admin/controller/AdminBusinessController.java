package kh.edu.istad.ite.features.admin.controller;

import kh.edu.istad.ite.features.admin.dto.request.BusinessStatusActionRequest;
import kh.edu.istad.ite.features.admin.service.AdminBusinessService;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/businesses")
@RequiredArgsConstructor
public class AdminBusinessController {

    private final AdminBusinessService adminBusinessService;

    @GetMapping
    public Page<BusinessResponse> getBusinesses(
            @RequestParam(required = false) BusinessOwnerStatus status,
            @RequestParam(required = false) Boolean isEnabled,
            @RequestParam(required = false) Boolean isClosed,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return adminBusinessService.getBusinesses(status, isEnabled, isClosed, categoryId, keyword, pageable);
    }

    @GetMapping("/{businessId}")
    public BusinessResponse getBusiness(@PathVariable UUID businessId) {
        return adminBusinessService.getBusiness(businessId);
    }

    @PatchMapping("/{businessId}/activate")
    public BusinessResponse activate(@PathVariable UUID businessId) {
        return adminBusinessService.activate(businessId);
    }

    @PatchMapping("/{businessId}/suspend")
    public BusinessResponse suspend(
            @PathVariable UUID businessId,
            @RequestBody(required = false) BusinessStatusActionRequest request
    ) {
        return adminBusinessService.suspend(businessId, request);
    }

    @PatchMapping("/{businessId}/enable")
    public BusinessResponse enable(@PathVariable UUID businessId) {
        return adminBusinessService.enable(businessId);
    }

    @PatchMapping("/{businessId}/disable")
    public BusinessResponse disable(
            @PathVariable UUID businessId,
            @RequestBody(required = false) BusinessStatusActionRequest request
    ) {
        return adminBusinessService.disable(businessId, request);
    }

    @PatchMapping("/{businessId}/close")
    public BusinessResponse close(
            @PathVariable UUID businessId,
            @RequestBody(required = false) BusinessStatusActionRequest request
    ) {
        return adminBusinessService.close(businessId, request);
    }

    @PatchMapping("/{businessId}/reopen")
    public BusinessResponse reopen(@PathVariable UUID businessId) {
        return adminBusinessService.reopen(businessId);
    }

    @DeleteMapping("/{businessId}")
    public BusinessResponse delete(@PathVariable UUID businessId) {
        return adminBusinessService.delete(businessId);
    }
}
