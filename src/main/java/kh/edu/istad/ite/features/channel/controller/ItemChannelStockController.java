package kh.edu.istad.ite.features.channel.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.channel.dto.ItemChannelStockResponse;
import kh.edu.istad.ite.features.channel.dto.SaveItemChannelStockRequest;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * How one item's stock is shared out between the channels that sell it.
 *
 * Scoped to the business and to the item, because that is how it is decided:
 * one shop, looking at one thing on its shelf, saying how much of it each
 * channel may sell.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/businesses/{businessId}/items/{itemId}/channel-stock")
public class ItemChannelStockController {

    private final ItemChannelStockService itemChannelStockService;

    @GetMapping
    public ItemChannelStockResponse findSplit(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId) {
        return itemChannelStockService.findSplit(businessId, itemId);
    }

    @PutMapping
    public ItemChannelStockResponse saveSplit(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @RequestBody SaveItemChannelStockRequest request) {
        return itemChannelStockService.saveSplit(businessId, itemId, request);
    }
}
