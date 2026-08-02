package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;

import java.util.List;

public interface SalesChannelService {
    List<SalesChannelResponse> findAllActive();
    List<SalesChannelItemResponse> findItemsByChannel(
            String channelCode
    );


}
