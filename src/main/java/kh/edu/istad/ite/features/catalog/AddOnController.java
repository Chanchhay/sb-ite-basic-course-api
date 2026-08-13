package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.AddOnResponse;
import kh.edu.istad.ite.features.catalog.dto.CreateAddOnRequest;
import kh.edu.istad.ite.features.catalog.dto.UpdateAddOnRequest;
import kh.edu.istad.ite.features.catalog.service.AddOnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/businesses/{businessId}/add-ons")
@RequiredArgsConstructor
public class AddOnController {

    private final AddOnService addOnService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public AddOnResponse createAddOn(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateAddOnRequest request
    ) {
        return addOnService.createAddOn(businessId, request);
    }

    @GetMapping
    public List<AddOnResponse> findAllAddOns(@PathVariable UUID businessId) {
        return addOnService.findAllAddOns(businessId);
    }

    @PutMapping("/{addOnId}")
    public AddOnResponse updateAddOn(
            @PathVariable UUID businessId,
            @PathVariable UUID addOnId,
            @Valid @RequestBody UpdateAddOnRequest request
    ) {
        return addOnService.updateAddOn(businessId, addOnId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{addOnId}")
    public void deleteAddOn(
            @PathVariable UUID businessId,
            @PathVariable UUID addOnId
    ) {
        addOnService.deleteAddOn(businessId, addOnId);
    }
}
