package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemUomConversionResponse;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantResponse;
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
                UUID currentUserId = UUID.fromString(SecurityUtils.extractUserId());
                UUID businessId = businessRepository.findByKeycloakUserId(currentUserId)
                                .map(Business::getId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));

                return itemChannelRepository.findBySalesChannelCodeAndBusinessIdAndIsEnabledTrue(channelCode, businessId)
                                .stream()
                                .map(ic -> new SalesChannelItemResponse(
                                                ic.getId(),
                                                atChannelPrices(
                                                                itemMapper.toResponse(ic.getItem()),
                                                                businessId,
                                                                channelCode)))
                                .toList();
        }

        /**
         * The same item, priced the way this channel charges for it.
         *
         * The till builds its menu from this endpoint, while the order path
         * prices every line through {@link ChannelPriceResolver} — so handing
         * back business prices here would show the customer one number and
         * bill them another. Every way the item sells is resolved: on its own,
         * as an option, and by the pack.
         *
         * An unpriced line stays unpriced: the resolver marks a price up, it
         * does not invent one.
         */
        private ItemResponse atChannelPrices(
                        ItemResponse item,
                        UUID businessId,
                        String channelCode) {

                UUID baseUnitId = item.unit() == null ? null : item.unit().id();

                return item.toBuilder()
                                .price(channelPriceResolver.priceFor(
                                                businessId, channelCode, item.price(),
                                                item.id(), null, null))
                                .variants(item.variants() == null ? null
                                                : item.variants().stream()
                                                                .map(variant -> new ItemVariantResponse(
                                                                                variant.id(),
                                                                                variant.slug(),
                                                                                variant.name(),
                                                                                variant.sku(),
                                                                                variant.barcode(),
                                                                                variant.imageUrl(),
                                                                                channelPriceResolver.priceFor(
                                                                                                businessId, channelCode,
                                                                                                variant.price(),
                                                                                                item.id(), variant.id(),
                                                                                                null),
                                                                                variant.available()))
                                                                .toList())
                                .uomConversions(item.uomConversions() == null ? null
                                                : item.uomConversions().stream()
                                                                .map(conversion -> new ItemUomConversionResponse(
                                                                                conversion.id(),
                                                                                conversion.unit(),
                                                                                conversion.variantId(),
                                                                                conversion.variantName(),
                                                                                conversion.factor(),
                                                                                channelPriceResolver.priceFor(
                                                                                                businessId, channelCode,
                                                                                                conversion.price(),
                                                                                                item.id(),
                                                                                                conversion.variantId(),
                                                                                                overrideUnitId(conversion,
                                                                                                                baseUnitId))))
                                                                .toList())
                                .build();
        }

        /**
         * Which unit an exception is keyed under.
         *
         * A conversion on the item's own base unit is the item sold plainly,
         * and that line is stored with no unit at all — keying it by the base
         * unit would look past the exception the shop actually set. Mirrors
         * how the order path asks the same question.
         */
        private UUID overrideUnitId(ItemUomConversionResponse conversion, UUID baseUnitId) {
                UUID unitId = conversion.unit() == null ? null : conversion.unit().id();

                return unitId == null || unitId.equals(baseUnitId) ? null : unitId;
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