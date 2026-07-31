package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;

import java.util.List;

public interface SalesChannelService {
    List<SalesChannelResponse> findAllActive();
    List<ItemResponse> findItemsByChannel(
            String channelCode
    );


}
