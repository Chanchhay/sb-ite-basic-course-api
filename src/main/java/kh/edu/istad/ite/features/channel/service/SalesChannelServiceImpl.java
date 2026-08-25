package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.channel.dto.CreateSalesChannelRequest;
import kh.edu.istad.ite.features.channel.dto.UpdateSalesChannelRequest;
import kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;
import kh.edu.istad.ite.features.channel.dto.SalesChannelResponse;
import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.config.security.SecurityUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesChannelServiceImpl
                implements SalesChannelService {

        private final SalesChannelRepository salesChannelRepository;
        private final ItemRepository itemRepository;
        private final ItemChannelRepository itemChannelRepository;
        private final ItemMapper itemMapper;
        private final BusinessRepository businessRepository;
        private final BusinessHelper businessHelper;
        private final ChannelPriceResolver channelPriceResolver;

        @Override
        public List<SalesChannelResponse> findAllActive() {

                return salesChannelRepository
                                .findAllByIsActiveTrueOrderByNameAsc()
                                .stream()
                                .map(this::mapToResponse)
                                .toList();

        }

        @Override
        public List<SalesChannelResponse> findAll() {
                return salesChannelRepository
                                .findAll()
                                .stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        @Override
        public List<SalesChannelItemResponse> findItemsByChannel(String channelCode) {
                UUID businessId = businessHelper.currentBusiness().getId();

                return itemChannelRepository.findBySalesChannelCodeAndBusinessIdAndIsEnabledTrue(channelCode, businessId)
                                .stream()
                                .map(ic -> new SalesChannelItemResponse(
                                                ic.getId(),
                                                channelPriceResolver.atChannelPrices(
                                                                itemMapper.toResponse(ic.getItem()),
                                                                businessId,
                                                                channelCode)))
                                .toList();
        }

        private ItemResponse mapToResponse(Item item) {

                return ItemResponse.builder()
                                .id(item.getId())
                                .name(item.getName())
                                .build();

        }

        private SalesChannelResponse mapToResponse(
                        SalesChannel channel) {

                return SalesChannelResponse.builder()
                                .id(channel.getId())
                                .name(channel.getName())
                                .code(channel.getCode())
                                .isActive(channel.getIsActive())
                                .build();

        }

        @Override
        @Transactional
        public SalesChannelResponse create(CreateSalesChannelRequest request) {
                if (salesChannelRepository.existsByCode(request.code())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel code already exists");
                }
                SalesChannel channel = new SalesChannel();
                channel.setName(request.name());
                channel.setCode(request.code());
                channel.setIsActive(request.isActive() != null ? request.isActive() : true);
                return mapToResponse(salesChannelRepository.save(channel));
        }

        @Override
        @Transactional
        public SalesChannelResponse update(UUID id, UpdateSalesChannelRequest request) {
                SalesChannel channel = salesChannelRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales channel not found"));
                if (request.name() != null) {
                        channel.setName(request.name());
                }
                if (request.code() != null && !request.code().equals(channel.getCode())) {
                        if (salesChannelRepository.existsByCode(request.code())) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel code already exists");
                        }
                        channel.setCode(request.code());
                }
                if (request.isActive() != null) {
                        channel.setIsActive(request.isActive());
                }
                return mapToResponse(salesChannelRepository.save(channel));
        }

        @Override
        @Transactional
        public void delete(UUID id) {
                SalesChannel channel = salesChannelRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales channel not found"));
                salesChannelRepository.delete(channel);
        }
}