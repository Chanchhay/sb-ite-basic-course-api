package kh.edu.istad.ite.features.channel.controller;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;
import kh.edu.istad.ite.features.channel.service.SalesChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/sales-channels")
@RequiredArgsConstructor
public class SalesChannelController {


    private final SalesChannelService salesChannelService;


    @GetMapping
    public List<SalesChannelResponse> findAll(){

        return salesChannelService.findAllActive();

    }

    @GetMapping("/{channelCode}/items")
    public List<ItemResponse> findItemsByChannel(
            @PathVariable String channelCode
    ){
        return salesChannelService.findItemsByChannel(channelCode);
    }

}