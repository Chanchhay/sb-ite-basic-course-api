package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies a business's single configured tax rate to a net (post-discount)
 * amount.
 *
 * Every channel — POS, web storefront, Telegram, Messenger — prices an order
 * through this one calculator, so a rate change in Business Settings changes
 * every receipt the same way instead of each channel re-deriving its own
 * number and drifting out of sync with the others.
 */
@Component
public class TaxCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Result(
            BigDecimal taxRate,
            TaxInclusionType inclusionType,
            BigDecimal taxAmount,
            BigDecimal total
    ) {
    }

    /**
     * @param netAmount the amount tax applies to — subtotal after discount,
     *                  never before it
     */
    public Result apply(Business business, BigDecimal netAmount, int scale) {
        TaxInclusionType inclusionType = business.getTaxInclusionType() != null
                ? business.getTaxInclusionType()
                : TaxInclusionType.EXCLUSIVE;

        BigDecimal net = netAmount == null ? BigDecimal.ZERO : netAmount;

        boolean enabled = Boolean.TRUE.equals(business.getTaxEnabled())
                && business.getTaxRate() != null
                && business.getTaxRate().compareTo(BigDecimal.ZERO) > 0;

        if (!enabled) {
            return new Result(
                    BigDecimal.ZERO, inclusionType, BigDecimal.ZERO, net.setScale(scale, RoundingMode.HALF_UP));
        }

        BigDecimal rate = business.getTaxRate();
        BigDecimal taxAmount;
        BigDecimal total;

        if (inclusionType == TaxInclusionType.INCLUSIVE) {
            // The rate is already baked into net, so pull it back out rather
            // than adding it on top: net / (1 + rate/100) is the pre-tax
            // price, and the difference is what tax that price incurs.
            BigDecimal divisor = BigDecimal.ONE.add(rate.divide(HUNDRED, 10, RoundingMode.HALF_UP));
            BigDecimal pretax = net.divide(divisor, scale, RoundingMode.HALF_UP);
            taxAmount = net.subtract(pretax).setScale(scale, RoundingMode.HALF_UP);
            total = net.setScale(scale, RoundingMode.HALF_UP);
        } else {
            taxAmount = net.multiply(rate).divide(HUNDRED, scale, RoundingMode.HALF_UP);
            total = net.add(taxAmount).setScale(scale, RoundingMode.HALF_UP);
        }

        return new Result(rate, inclusionType, taxAmount, total);
    }
}
