package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.channel.dto.CreateSalesChannelRequest;
import kh.edu.istad.ite.features.channel.dto.UpdateSalesChannelRequest;
import kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;

import java.util.List;
import java.util.UUID;

public interface SalesChannelService {
    List<SalesChannelResponse> findAllActive();

    List<SalesChannelItemResponse> findItemsByChannel(
            String channelCode);

    SalesChannelResponse create(CreateSalesChannelRequest request);

    SalesChannelResponse update(UUID id, UpdateSalesChannelRequest request);

    void delete(UUID id);
}
