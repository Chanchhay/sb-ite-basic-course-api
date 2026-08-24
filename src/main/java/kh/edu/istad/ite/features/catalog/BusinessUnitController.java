package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.BusinessUnitRequest;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.features.catalog.service.BusinessUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/businesses/{businessId}/units")
@RequiredArgsConstructor
public class BusinessUnitController {

    private final BusinessUnitService businessUnitService;

    /** Platform units and this business's own, in one list. */
    @GetMapping
    public Page<UnitResponse> findSelectableUnits(
            @PathVariable UUID businessId,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return businessUnitService.findSelectableUnits(businessId, pageable);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public UnitResponse createUnit(
            @PathVariable UUID businessId,
            @Valid @RequestBody BusinessUnitRequest request
    ) {
        return businessUnitService.createUnit(businessId, request);
    }

    @PutMapping("/{unitId}")
    public UnitResponse updateUnit(
            @PathVariable UUID businessId,
            @PathVariable UUID unitId,
            @Valid @RequestBody BusinessUnitRequest request
    ) {
        return businessUnitService.updateUnit(businessId, unitId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{unitId}")
    public void deleteUnit(
            @PathVariable UUID businessId,
            @PathVariable UUID unitId
    ) {
        businessUnitService.deleteUnit(businessId, unitId);
    }
}
