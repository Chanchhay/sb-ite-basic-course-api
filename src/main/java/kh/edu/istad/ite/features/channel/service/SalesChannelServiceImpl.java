package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;
import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesChannelServiceImpl
        implements SalesChannelService {


    private final SalesChannelRepository salesChannelRepository;
    private final ItemRepository itemRepository;
    private final ItemChannelRepository itemChannelRepository;
    private final ItemMapper itemMapper;


    @Override
    public List<SalesChannelResponse> findAllActive() {

        return salesChannelRepository
                .findAllByIsActiveTrueOrderByNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();

    }

    @Override
    public List<SalesChannelItemResponse> findItemsByChannel(String channelCode) {
        return itemChannelRepository.findBySalesChannelCodeAndIsEnabledTrue(channelCode)
                .stream()
                .map(ic -> new SalesChannelItemResponse(
                        ic.getId(),
                        itemMapper.toResponse(ic.getItem())
                ))
                .toList();
    }
    private ItemResponse mapToResponse(Item item){

        return ItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .build();

    }


    private SalesChannelResponse mapToResponse(
            SalesChannel channel
    ){

        return SalesChannelResponse.builder()
                .id(channel.getId())
                .name(channel.getName())
                .code(channel.getCode())
                .isActive(channel.getIsActive())
                .build();

    }



}