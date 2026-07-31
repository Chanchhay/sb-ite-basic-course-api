package kh.edu.istad.ite.features.channel.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.channel.dto.CreateItemChannelRequest;
import kh.edu.istad.ite.features.channel.dto.ItemChannelResponse;
import kh.edu.istad.ite.features.channel.dto.ToggleItemChannelRequest;
import kh.edu.istad.ite.features.channel.service.ItemChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/item-channels")
public class ItemChannelController {
    private final ItemChannelService itemChannelService;

    @PostMapping
    public ItemChannelResponse create(@RequestBody
                                          @Valid
                                          CreateItemChannelRequest request){
        return itemChannelService.create(request);
    }

    @GetMapping("/items/{itemId}")
    public List<ItemChannelResponse> findByItem(@PathVariable UUID itemId){
        return itemChannelService.findByItem(itemId);
    }

    @PatchMapping("/{id}/toggle")
    public ItemChannelResponse toggle(
            @PathVariable UUID id,
            @RequestBody
            ToggleItemChannelRequest request
    ){
        return itemChannelService.toggle(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        itemChannelService.delete(id);
    }


}
