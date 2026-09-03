package kh.edu.istad.ite.features.channel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse;
import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse.ChannelPriceLineDto;
import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse.OverrideDto;
import kh.edu.istad.ite.features.channel.dto.ChannelScheduleDto;
import kh.edu.istad.ite.features.channel.dto.SaveChannelListingRequest;
import kh.edu.istad.ite.features.channel.entity.BusinessChannelSettings;
import kh.edu.istad.ite.features.channel.entity.ItemChannel;
import kh.edu.istad.ite.features.channel.entity.ItemChannelPrice;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.BusinessChannelSettingsRepository;
import kh.edu.istad.ite.features.channel.repository.ItemChannelPriceRepository;
import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import kh.edu.istad.ite.shared.enums.PriceOverrideKind;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.cache.BusinessCacheEvictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What one sales channel sells, charges and opens.
 *
 * The business price is never touched. A channel keeps its own exceptions
 * beside it, so raising a price on the Set Price screen moves every channel
 * that never disagreed — and "reset to base" is a row being deleted rather
 * than a guess at what the number used to be.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelPricingServiceImpl implements ChannelPricingService {

    private final BusinessHelper businessHelper;
    private final SalesChannelRepository salesChannelRepository;
    private final ItemChannelRepository itemChannelRepository;
    private final ItemChannelPriceRepository itemChannelPriceRepository;
    private final BusinessChannelSettingsRepository settingsRepository;
    private final ItemRepository itemRepository;
    private final UnitRepository unitRepository;
    private final BusinessCacheEvictor businessCacheEvictor;

    /** Its own, like every other JSON reader here — the app publishes no bean. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public ChannelListingResponse findListing(UUID businessId, UUID channelId) {
        businessHelper.findOwnedBusiness(businessId);
        SalesChannel channel = findChannel(channelId);

        return toResponse(
                channel,
                settingsRepository
                        .findByBusinessIdAndSalesChannelId(businessId, channelId)
                        .orElse(null),
                enabledItemIds(businessId, channelId),
                itemChannelPriceRepository.findForBusinessChannel(businessId, channelId));
    }

    @Override
    @Transactional
    public ChannelListingResponse saveListing(
            UUID businessId, UUID channelId, SaveChannelListingRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        businessCacheEvictor.evictStorefront(businessId);
        SalesChannel channel = findChannel(channelId);

        BusinessChannelSettings settings = settingsRepository
                .findByBusinessIdAndSalesChannelId(businessId, channelId)
                .orElseGet(() -> {
                    BusinessChannelSettings fresh = new BusinessChannelSettings();
                    fresh.setBusiness(business);
                    fresh.setSalesChannel(channel);
                    return fresh;
                });

        if (request.globalRule() != null) {
            settings.setOverrideKind(parseKind(request.globalRule().kind()));
            settings.setOverrideValue(request.globalRule().value());
        }
        if (request.schedule() != null) {
            settings.setScheduleJson(writeSchedule(request.schedule()));
        }

        settingsRepository.save(settings);

        if (request.enabledItemIds() != null) {
            replaceEnabledItems(businessId, channel, request.enabledItemIds());
        }
        if (request.overrides() != null) {
            replaceOverrides(businessId, channel, request.overrides());
        }

        return toResponse(
                channel,
                settings,
                enabledItemIds(businessId, channelId),
                itemChannelPriceRepository.findForBusinessChannel(businessId, channelId));
    }

    /**
     * Brings the channel's item list in line with what was sent.
     *
     * A link that stays is switched rather than deleted and written again:
     * unlisting an item for a week and putting it back should not lose that it
     * was ever set up here, and Hibernate flushes inserts before deletes, so a
     * delete-and-recreate would collide with the unique key it was about to
     * free.
     */
    private void replaceEnabledItems(UUID businessId, SalesChannel channel, List<UUID> itemIds) {
        Set<UUID> wanted = new LinkedHashSet<>(itemIds.stream().filter(Objects::nonNull).toList());
        List<ItemChannel> existing = itemChannelRepository
                .findBySalesChannelIdAndBusinessId(channel.getId(), businessId);
        Set<UUID> seen = new HashSet<>();

        for (ItemChannel link : existing) {
            UUID itemId = link.getItem().getId();
            seen.add(itemId);
            link.setIsEnabled(wanted.contains(itemId));
        }

        itemChannelRepository.saveAll(existing);

        for (UUID itemId : wanted) {
            if (seen.contains(itemId)) continue;

            ItemChannel link = new ItemChannel();
            link.setItem(findItem(itemId, businessId));
            link.setSalesChannel(channel);
            link.setIsEnabled(true);
            itemChannelRepository.save(link);
        }
    }

    /**
     * Brings the channel's exceptions in line with what was sent.
     *
     * A line the screen no longer names has no exception any more, which is a
     * deletion rather than a rule of zero — the two would price identically
     * today and diverge the moment the business price moves.
     */
    private void replaceOverrides(
            UUID businessId, SalesChannel channel, List<ChannelPriceLineDto> lines) {
        Map<String, ItemChannelPrice> existing = new HashMap<>();

        for (ItemChannelPrice price :
                itemChannelPriceRepository.findForBusinessChannel(businessId, channel.getId())) {
            existing.put(price.lineKey(), price);
        }

        Set<String> seen = new HashSet<>();
        List<ItemChannelPrice> saved = new ArrayList<>();

        for (ChannelPriceLineDto line : lines) {
            if (line.itemId() == null) continue;

            PriceOverrideKind kind = parseKind(line.kind());

            // "Same as base" is the absence of an exception, so it is never
            // stored — otherwise every line the shop looked at and left alone
            // would become a row that has to be kept in step with the base.
            if (kind == PriceOverrideKind.INHERIT) continue;

            Item item = findItem(line.itemId(), businessId);
            ItemVariant variant = findVariant(item, line.variantId());
            Unit unit = findUnit(line.unitId(), businessId);
            String key = ItemChannelPrice.lineKey(
                    item.getId(),
                    variant == null ? null : variant.getId(),
                    unit == null ? null : unit.getId());

            if (!seen.add(key)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "\"" + item.getName() + "\" has two different prices on this channel");
            }

            ItemChannelPrice price = existing.get(key);

            if (price == null) {
                price = new ItemChannelPrice();
                price.setSalesChannel(channel);
                price.setItem(item);
                price.setVariant(variant);
                price.setUnit(unit);
            }

            price.setOverrideKind(kind);
            price.setOverrideValue(line.value() == null ? BigDecimal.ZERO : line.value());
            saved.add(price);
        }

        itemChannelPriceRepository.saveAll(saved);

        List<ItemChannelPrice> gone = existing.entrySet().stream()
                .filter(entry -> !seen.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        if (!gone.isEmpty()) {
            itemChannelPriceRepository.deleteAll(gone);
        }
    }

    private List<UUID> enabledItemIds(UUID businessId, UUID channelId) {
        return itemChannelRepository
                .findBySalesChannelIdAndBusinessId(channelId, businessId)
                .stream()
                .filter(link -> Boolean.TRUE.equals(link.getIsEnabled()))
                .map(link -> link.getItem().getId())
                .toList();
    }

    private ChannelListingResponse toResponse(
            SalesChannel channel,
            BusinessChannelSettings settings,
            List<UUID> enabledItemIds,
            List<ItemChannelPrice> overrides) {
        ChannelScheduleDto schedule = settings == null
                ? null
                : readSchedule(settings.getScheduleJson());

        return new ChannelListingResponse(
                channel.getId(),
                channel.getName(),
                channel.getCode(),
                channel.getIsActive(),
                new OverrideDto(
                        settings == null
                                ? PriceOverrideKind.INHERIT.name()
                                : settings.getOverrideKind().name(),
                        settings == null ? null : settings.getOverrideValue()),
                schedule,
                schedule == null || schedule.isOpenAt(LocalDateTime.now()),
                enabledItemIds,
                overrides.stream()
                        .map(price -> new ChannelPriceLineDto(
                                price.getItem().getId(),
                                price.getVariant() == null ? null : price.getVariant().getId(),
                                price.getUnit() == null ? null : price.getUnit().getId(),
                                price.getOverrideKind().name(),
                                price.getOverrideValue()))
                        .toList());
    }

    private SalesChannel findChannel(UUID channelId) {
        return salesChannelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Sales channel has not been found"));
    }

    private Item findItem(UUID itemId, UUID businessId) {
        return itemRepository.findByIdAndBusinessId(itemId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item has not been found: " + itemId));
    }

    private ItemVariant findVariant(Item item, UUID variantId) {
        if (variantId == null) return null;

        return item.getVariants().stream()
                .filter(candidate -> variantId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "That option is not one of \"" + item.getName() + "\"'s"));
    }

    private Unit findUnit(UUID unitId, UUID businessId) {
        if (unitId == null) return null;

        return unitRepository.findByIdAndBusinessId(unitId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unit has not been found: " + unitId));
    }

    private PriceOverrideKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) return PriceOverrideKind.INHERIT;

        try {
            return PriceOverrideKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "\"" + kind + "\" is not a pricing rule");
        }
    }

    private String writeSchedule(ChannelScheduleDto schedule) {
        try {
            return objectMapper.writeValueAsString(schedule);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Those opening hours could not be saved");
        }
    }

    /**
     * Unreadable hours are treated as none rather than as a failure: the shop
     * should still be able to open its own screen and set them again.
     */
    private ChannelScheduleDto readSchedule(String json) {
        if (json == null || json.isBlank()) return null;

        try {
            return objectMapper.readValue(json, ChannelScheduleDto.class);
        } catch (JsonProcessingException e) {
            log.warn("Ignoring unreadable channel schedule", e);
            return null;
        }
    }
}
