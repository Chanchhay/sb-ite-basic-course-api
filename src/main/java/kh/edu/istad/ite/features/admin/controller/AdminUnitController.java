package kh.edu.istad.ite.features.admin.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.admin.dto.request.UnitUpsertRequest;
import kh.edu.istad.ite.features.admin.service.AdminUnitService;
import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/units")
@RequiredArgsConstructor
public class AdminUnitController {

    private final AdminUnitService adminUnitService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public UnitResponse createUnit(@Valid @RequestBody UnitUpsertRequest request) {
        return adminUnitService.createUnit(request);
    }

    @PutMapping("/{unitId}")
    public UnitResponse updateUnit(
            @PathVariable UUID unitId,
            @Valid @RequestBody UnitUpsertRequest request
    ) {
        return adminUnitService.updateUnit(unitId, request);
    }

    @GetMapping("/{unitId}")
    public UnitResponse getUnitById(@PathVariable UUID unitId) {
        return adminUnitService.getUnitById(unitId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{unitId}")
    public void deleteUnit(@PathVariable UUID unitId) {
        adminUnitService.deleteUnit(unitId);
    }
}
