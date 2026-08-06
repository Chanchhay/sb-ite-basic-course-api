package kh.edu.istad.ite.features.channel.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.channel.dto.CreateSalesChannelRequest;
import kh.edu.istad.ite.features.channel.dto.UpdateSalesChannelRequest;
import kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;
import kh.edu.istad.ite.features.channel.service.SalesChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales-channels")
@RequiredArgsConstructor
public class SalesChannelController {

    private final SalesChannelService salesChannelService;

    @GetMapping
    public List<SalesChannelResponse> findAll(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return all ? salesChannelService.findAll() : salesChannelService.findAllActive();
    }

    @GetMapping("/{channelCode}/items")
    public List<SalesChannelItemResponse> findItemsByChannel(
            @PathVariable String channelCode) {
        return salesChannelService.findItemsByChannel(channelCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesChannelResponse create(@Valid @RequestBody CreateSalesChannelRequest request) {
        return salesChannelService.create(request);
    }

    @PutMapping("/{id}")
    public SalesChannelResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSalesChannelRequest request) {
        return salesChannelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        salesChannelService.delete(id);
    }
}