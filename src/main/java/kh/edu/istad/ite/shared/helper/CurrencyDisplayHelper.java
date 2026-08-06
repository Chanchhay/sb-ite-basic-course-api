package kh.edu.istad.ite.shared.helper;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import kh.edu.istad.ite.features.business.repository.BusinessCurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Captures the second currency an amount was shown in at the time of sale.
 *
 * <p>Rates move, so a receipt that recomputed its riel total on every render
 * would stop matching the figure the customer was handed. Orders and sales
 * therefore store the rate they were priced at rather than a reference to the
 * business configuration.
 */
@Component
@RequiredArgsConstructor
public class CurrencyDisplayHelper {

    private static final int RATE_SCALE = 8;

    private final BusinessCurrencyRepository businessCurrencyRepository;

    /** The display currency and its rate, or empty when there is no second currency. */
    public Optional<Snapshot> snapshot(Business business, String sourceCode) {
        String displayCode = business.getDisplayCurrency();

        if (!StringUtils.hasText(displayCode)
                || !StringUtils.hasText(sourceCode)
                || displayCode.equalsIgnoreCase(sourceCode)) {
            return Optional.empty();
        }

        BigDecimal sourceRate = rateOf(business, sourceCode);
        BigDecimal displayRate = rateOf(business, displayCode);

        if (sourceRate == null
                || displayRate == null
                || sourceRate.compareTo(BigDecimal.ZERO) <= 0
                || displayRate.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        return Optional.of(new Snapshot(
                displayCode.toUpperCase(),
                displayRate.divide(sourceRate, RATE_SCALE, RoundingMode.HALF_UP)
        ));
    }

    private BigDecimal rateOf(Business business, String code) {
        return businessCurrencyRepository
                .findByBusinessIdAndCodeIgnoreCase(business.getId(), code)
                .map(BusinessCurrency::getExchangeRate)
                .orElse(null);
    }

    /** Units of {@code currency} per one unit of the amount's own currency. */
    public record Snapshot(String currency, BigDecimal rate) {
    }
}
