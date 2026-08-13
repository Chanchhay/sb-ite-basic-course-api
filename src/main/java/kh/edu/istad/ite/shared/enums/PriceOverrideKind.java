package kh.edu.istad.ite.shared.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a sales channel departs from the price the business set.
 *
 * Stored as the rule rather than the number it works out to, which is the whole
 * point: "delivery is 15% dearer" stays true when the shop repices, where a
 * hard-typed amount silently goes stale the first time costs move.
 */
public enum PriceOverrideKind {

    /** No exception — the channel charges the business price. */
    INHERIT,

    /** A percentage on top of the business price. */
    MARKUP_PERCENT,

    /** A flat amount on top of the business price. */
    MARKUP_AMOUNT;

    /**
     * What this rule charges for something the business prices at {@code base}.
     *
     * An unpriced line stays unpriced: a percentage of nothing is nothing, and
     * a channel is not the place a price first comes into existence.
     */
    public BigDecimal apply(BigDecimal base, BigDecimal value) {
        if (base == null || this == INHERIT || value == null) {
            return base;
        }

        BigDecimal priced = this == MARKUP_PERCENT
                ? base.multiply(BigDecimal.ONE.add(value.movePointLeft(2)))
                : base.add(value);

        // Never below nothing: a discount deeper than the price is a mistake,
        // and charging a negative amount would pay the customer to take it.
        return priced.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
