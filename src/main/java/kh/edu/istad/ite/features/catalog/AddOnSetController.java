package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.AddOnSetRequest;
import kh.edu.istad.ite.features.catalog.dto.AddOnSetResponse;
import kh.edu.istad.ite.features.catalog.service.AddOnSetService;
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
@RequestMapping("/api/v1/businesses/{businessId}/add-on-sets")
@RequiredArgsConstructor
public class AddOnSetController {

    private final AddOnSetService addOnSetService;

    @GetMapping
    public List<AddOnSetResponse> findAllAddOnSets(@PathVariable UUID businessId) {
        return addOnSetService.findAllAddOnSets(businessId);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public AddOnSetResponse createAddOnSet(
            @PathVariable UUID businessId,
            @Valid @RequestBody AddOnSetRequest request
    ) {
        return addOnSetService.createAddOnSet(businessId, request);
    }

    @PutMapping("/{setId}")
    public AddOnSetResponse updateAddOnSet(
            @PathVariable UUID businessId,
            @PathVariable UUID setId,
            @Valid @RequestBody AddOnSetRequest request
    ) {
        return addOnSetService.updateAddOnSet(businessId, setId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{setId}")
    public void deleteAddOnSet(
            @PathVariable UUID businessId,
            @PathVariable UUID setId
    ) {
        addOnSetService.deleteAddOnSet(businessId, setId);
    }
}
