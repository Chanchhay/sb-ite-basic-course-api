package kh.edu.istad.ite.features.business;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessRequest;
import kh.edu.istad.ite.features.business.service.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public BusinessResponse createBusiness(@Valid @RequestBody CreateBusinessRequest request) {
        return businessService.createBusiness(request);
    }

    @GetMapping("/me")
    public BusinessResponse getMyBusiness() {
        return businessService.getMyBusiness();
    }

    @GetMapping("/{businessId}")
    public BusinessResponse getBusiness(@PathVariable UUID businessId) {
        return businessService.getBusiness(businessId);
    }

    @PutMapping("/{businessId}")
    public BusinessResponse updateBusiness(
            @PathVariable UUID businessId,
            @Valid @RequestBody UpdateBusinessRequest request
    ) {
        return businessService.updateBusiness(businessId, request);
    }

    @PutMapping("/{businessId}/delete")
    public BusinessResponse deleteBusiness(@PathVariable UUID businessId) {
        return businessService.deleteBusiness(businessId);
    }
}
