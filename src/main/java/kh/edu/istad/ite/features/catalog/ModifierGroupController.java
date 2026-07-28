package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.ModifierGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ModifierGroupResponse;
import kh.edu.istad.ite.features.catalog.service.ModifierGroupService;
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
@RequestMapping("/api/v1/businesses/{businessId}/items/{itemId}/modifier-groups")
@RequiredArgsConstructor
public class ModifierGroupController {

    private final ModifierGroupService modifierGroupService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ModifierGroupResponse createGroup(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ModifierGroupRequest request
    ) {
        return modifierGroupService.createGroup(businessId, itemId, request);
    }

    @GetMapping
    public List<ModifierGroupResponse> findAllGroups(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId
    ) {
        return modifierGroupService.findAllGroups(businessId, itemId);
    }

    @GetMapping("/{groupId}")
    public ModifierGroupResponse findGroupById(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PathVariable UUID groupId
    ) {
        return modifierGroupService.findGroupById(businessId, itemId, groupId);
    }

    @PutMapping("/{groupId}")
    public ModifierGroupResponse updateGroup(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PathVariable UUID groupId,
            @Valid @RequestBody ModifierGroupRequest request
    ) {
        return modifierGroupService.updateGroup(businessId, itemId, groupId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{groupId}")
    public void deleteGroup(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PathVariable UUID groupId
    ) {
        modifierGroupService.deleteGroup(businessId, itemId, groupId);
    }
}