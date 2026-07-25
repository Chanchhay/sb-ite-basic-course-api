package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.CreateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemSubGroupResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemGroupRequest;
import kh.edu.istad.ite.features.catalog.service.ItemGroupService;
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
@RequestMapping("/api/v1/businesses/{businessId}/item-groups")
@RequiredArgsConstructor
public class ItemGroupController {

    private final ItemGroupService itemGroupService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ItemSubGroupResponse createItemGroup(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateItemGroupRequest request
    ) {
        return itemGroupService.createItemGroup(businessId, request);
    }

    @GetMapping
    public List<ItemGroupResponse> findAllItemGroups(@PathVariable UUID businessId) {
        return itemGroupService.findAllItemGroups(businessId);
    }

    @PutMapping("/{itemGroupId}")
    public ItemSubGroupResponse updateItemGroup(
            @PathVariable UUID businessId,
            @PathVariable UUID itemGroupId,
            @Valid @RequestBody UpdateItemGroupRequest request
    ) {
        return itemGroupService.updateItemGroup(businessId, itemGroupId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{itemGroupId}")
    public void deleteItemGroup(
            @PathVariable UUID businessId,
            @PathVariable UUID itemGroupId
    ) {
        itemGroupService.deleteItemGroup(businessId, itemGroupId);
    }
}
