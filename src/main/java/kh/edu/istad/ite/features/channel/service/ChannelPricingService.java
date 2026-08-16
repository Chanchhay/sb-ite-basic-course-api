package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse;
import kh.edu.istad.ite.features.channel.dto.SaveChannelListingRequest;

import java.util.UUID;

public interface ChannelPricingService {

    ChannelListingResponse findListing(UUID businessId, UUID channelId);

    ChannelListingResponse saveListing(
            UUID businessId, UUID channelId, SaveChannelListingRequest request);
}
