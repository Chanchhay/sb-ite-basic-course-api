package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.CreateItemRequest;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateItemRequest;
import kh.edu.istad.ite.features.catalog.service.ItemService;
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
@RequestMapping("/api/v1/businesses/{businessId}/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ItemResponse createItem(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateItemRequest request
    ) {
        return itemService.createItem(businessId, request);
    }

    @GetMapping
    public List<ItemResponse> findAllItems(@PathVariable UUID businessId) {
        return itemService.findAllItems(businessId);
    }

    @GetMapping("/{itemId}")
    public ItemResponse findItemById(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId
    ) {
        return itemService.findItemById(businessId, itemId);
    }

    @PutMapping("/{itemId}")
    public ItemResponse updateItem(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        return itemService.updateItem(businessId, itemId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{itemId}")
    public void deleteItem(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId
    ) {
        itemService.deleteItem(businessId, itemId);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/barcode/{barcode}")
    public ItemResponse findItemByBarcode(
            @PathVariable UUID businessId,
            @PathVariable String barcode
    ) {
        return itemService.findItemByBarcode(businessId, barcode);
    }
}
