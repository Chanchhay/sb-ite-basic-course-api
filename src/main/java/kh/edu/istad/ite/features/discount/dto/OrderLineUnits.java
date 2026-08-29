package kh.edu.istad.ite.features.discount.dto;

import java.math.BigDecimal;

/**
 * One cart/order line's own unit price and quantity — what
 * {@link kh.edu.istad.ite.features.discount.service.DiscountApplicationService#resolveOrderDiscount}
 * needs to correctly spread a storewide BUY_X_GET_Y bundle across whichever
 * units are actually cheapest, when the order mixes different items rather
 * than repeating just one.
 */
public record OrderLineUnits(BigDecimal unitPrice, int quantity) {
}
