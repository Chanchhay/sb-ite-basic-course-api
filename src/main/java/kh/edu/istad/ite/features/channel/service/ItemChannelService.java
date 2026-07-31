package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.channel.dto.CreateItemChannelRequest;
import kh.edu.istad.ite.features.channel.dto.ItemChannelResponse;
import kh.edu.istad.ite.features.channel.dto.ToggleItemChannelRequest;

import java.util.List;
import java.util.UUID;

public interface ItemChannelService {
    ItemChannelResponse create(CreateItemChannelRequest request);

    List<ItemChannelResponse> findByItem(UUID itemId);

    ItemChannelResponse toggle(UUID id, ToggleItemChannelRequest request);

    void delete(UUID id);

}
