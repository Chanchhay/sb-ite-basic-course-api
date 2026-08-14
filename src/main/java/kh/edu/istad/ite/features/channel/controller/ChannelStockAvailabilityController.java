package kh.edu.istad.ite.features.channel.controller;

import kh.edu.istad.ite.features.channel.dto.ChannelStockAvailabilityResponse;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * What one channel may still sell, for everything it sells.
 *
 * The till shows a figure beside every item on the screen and cannot ask about
 * them one at a time, so this answers for the whole catalogue at once. Only
 * items the shop has split appear — the rest have no ceiling, and the till
 * already knows what is on the shelf.
 *
 * Addressed by channel code rather than id because that is what a till knows
 * itself as: it is the POS, it is not a UUID it had to look up.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/businesses/{businessId}/sales-channels/{channelCode}/stock")
public class ChannelStockAvailabilityController {

    private final ItemChannelStockService itemChannelStockService;

    @GetMapping
    public List<ChannelStockAvailabilityResponse> findAvailability(
            @PathVariable UUID businessId,
            @PathVariable String channelCode) {
        return itemChannelStockService.findChannelAvailability(businessId, channelCode);
    }
}
