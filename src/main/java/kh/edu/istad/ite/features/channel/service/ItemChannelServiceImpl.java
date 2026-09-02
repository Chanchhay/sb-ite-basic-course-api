package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.config.CacheNames;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.channel.dto.CreateItemChannelRequest;
import kh.edu.istad.ite.features.channel.dto.ItemChannelResponse;
import kh.edu.istad.ite.features.channel.dto.ToggleItemChannelRequest;
import kh.edu.istad.ite.features.channel.entity.ItemChannel;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemChannelServiceImpl implements ItemChannelService {


    private final ItemChannelRepository itemChannelRepository;

    private final ItemRepository itemRepository;

    private final SalesChannelRepository salesChannelRepository;


    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_STORE_ITEMS, allEntries = true)
    public ItemChannelResponse create(
            CreateItemChannelRequest request
    ) {

        if(itemChannelRepository.existsByItemIdAndSalesChannelId(
                request.itemId(),
                request.salesChannelId()
        )){

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Item already assigned to this channel"
            );
        }


        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Item not found"
                        )
                );


        SalesChannel channel =
                salesChannelRepository.findById(
                                request.salesChannelId()
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Sales channel not found"
                                )
                        );


        ItemChannel itemChannel = new ItemChannel();
        itemChannel.setItem(item);
        itemChannel.setSalesChannel(channel);
        itemChannel.setIsEnabled(true);
        ItemChannel saved = itemChannelRepository.save(itemChannel);


        return mapToResponse(saved);
    }



    @Override
    @Transactional(readOnly = true)
    public List<ItemChannelResponse> findByItem(
            UUID itemId
    ){

        return itemChannelRepository.findByItemId(itemId)
                .stream()
                .map(this::mapToResponse)
                .toList();

    }



    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_STORE_ITEMS, allEntries = true)
    public ItemChannelResponse toggle(
            UUID id,
            ToggleItemChannelRequest request
    ){

        ItemChannel itemChannel =
                itemChannelRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Item channel not found"
                                )
                        );

        itemChannel.setIsEnabled(
                request.enabled()
        );

        ItemChannel saved =
                itemChannelRepository.save(itemChannel);
        return mapToResponse(saved);

    }



    @Override
    @CacheEvict(cacheNames = CacheNames.PUBLIC_STORE_ITEMS, allEntries = true)
    public void delete(
            UUID id
    ){

        ItemChannel itemChannel =
                itemChannelRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Item channel not found"
                                )
                        );
        itemChannelRepository.delete(itemChannel);
    }


    private ItemChannelResponse mapToResponse(
            ItemChannel itemChannel
    ){
        return ItemChannelResponse.builder()
                .id(itemChannel.getId())
                .itemId(
                        itemChannel.getItem().getId()
                )
                .itemName(
                        itemChannel.getItem().getName()
                )
                .salesChannelId(
                        itemChannel.getSalesChannel().getId()
                )

                .channelName(
                        itemChannel.getSalesChannel().getName()
                )

                .channelCode(
                        itemChannel.getSalesChannel().getCode()
                )

                .enabled(
                        itemChannel.getIsEnabled()
                )

                .build();
    }

}
