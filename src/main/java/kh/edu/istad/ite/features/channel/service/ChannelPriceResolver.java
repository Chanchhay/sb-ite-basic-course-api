package kh.edu.istad.ite.features.channel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.features.channel.dto.ChannelScheduleDto;
import kh.edu.istad.ite.features.channel.entity.BusinessChannelSettings;
import kh.edu.istad.ite.features.channel.entity.ItemChannelPrice;
import kh.edu.istad.ite.features.channel.repository.BusinessChannelSettingsRepository;
import kh.edu.istad.ite.features.channel.repository.ItemChannelPriceRepository;
import kh.edu.istad.ite.shared.enums.PriceOverrideKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What a channel actually charges, and whether it is taking orders.
 *
 * This is where channel pricing stops being a screen and starts being money:
 * an order priced without it would charge the business price no matter which
 * channel it came through, and every exception the shop set would be
 * decoration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelPriceResolver {

    private final ItemChannelPriceRepository itemChannelPriceRepository;
    private final BusinessChannelSettingsRepository settingsRepository;

    /** Its own, like every other JSON reader here — the app publishes no bean. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The price this channel charges for one line.
     *
     * The line's own exception wins; failing that the channel's blanket rule
     * applies; failing that the business price stands. An unpriced line stays
     * unpriced — a channel marks a price up, it does not invent one.
     */
    public BigDecimal priceFor(
            UUID businessId,
            String channelCode,
            BigDecimal basePrice,
            UUID itemId,
            UUID variantId,
            UUID unitId) {
        if (basePrice == null || channelCode == null || itemId == null) {
            return basePrice;
        }

        ItemChannelPrice line = itemChannelPriceRepository
                .findLine(channelCode, itemId, variantId, unitId)
                .orElse(null);

        if (line != null && line.getOverrideKind() != PriceOverrideKind.INHERIT) {
            return line.getOverrideKind().apply(basePrice, line.getOverrideValue());
        }

        BusinessChannelSettings settings = settingsRepository
                .findByBusinessIdAndSalesChannelCode(businessId, channelCode)
                .orElse(null);

        if (settings == null || settings.getOverrideKind() == PriceOverrideKind.INHERIT) {
            return basePrice;
        }

        return settings.getOverrideKind().apply(basePrice, settings.getOverrideValue());
    }

    /**
     * Refuses an order placed while the channel is shut.
     *
     * Opening hours that nothing enforces are a note to self. A shop that set
     * them meant them — and the message says when it does open, because "we
     * are closed" without that is the most annoying sentence in retail.
     */
    public void requireOpen(UUID businessId, String channelCode) {
        if (channelCode == null) return;

        ChannelScheduleDto schedule = settingsRepository
                .findByBusinessIdAndSalesChannelCode(businessId, channelCode)
                .map(BusinessChannelSettings::getScheduleJson)
                .map(this::readSchedule)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();

        if (schedule == null || schedule.isOpenAt(now)) return;

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This channel is closed right now — it is "
                        + schedule.describeDay(now.getDayOfWeek()) + ".");
    }

    /** Unreadable hours never close a shop that is trying to trade. */
    private ChannelScheduleDto readSchedule(String json) {
        if (json == null || json.isBlank()) return null;

        try {
            return objectMapper.readValue(json, ChannelScheduleDto.class);
        } catch (Exception e) {
            log.warn("Ignoring unreadable channel schedule", e);
            return null;
        }
    }
}
