package kh.edu.istad.ite.features.channel.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse;
import kh.edu.istad.ite.features.channel.dto.SaveChannelListingRequest;
import kh.edu.istad.ite.features.channel.service.ChannelPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * One business's arrangement with one sales channel.
 *
 * Scoped to the business because a channel is shared — every shop has a
 * counter — while what it sells there, charges there and opens there is the
 * shop's alone.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/businesses/{businessId}/sales-channels/{channelId}")
public class ChannelPricingController {

    private final ChannelPricingService channelPricingService;

    @GetMapping("/listing")
    public ChannelListingResponse findListing(
            @PathVariable UUID businessId,
            @PathVariable UUID channelId) {
        return channelPricingService.findListing(businessId, channelId);
    }

    @PutMapping("/listing")
    public ChannelListingResponse saveListing(
            @PathVariable UUID businessId,
            @PathVariable UUID channelId,
            @Valid @RequestBody SaveChannelListingRequest request) {
        return channelPricingService.saveListing(businessId, channelId, request);
    }
}
