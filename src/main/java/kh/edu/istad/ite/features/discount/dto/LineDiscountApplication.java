package kh.edu.istad.ite.features.discount.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single, already-condition-checked answer to "how much does this
 * discount take off, right now, given what's actually known" — computed by
 * {@link kh.edu.istad.ite.features.discount.service.DiscountApplicationService}
 * and used identically by browsing, add-to-cart, the cart display, and
 * checkout, so the four of them can never disagree about the same discount.
 */
public record LineDiscountApplication(
        UUID discountId,
        String label,
        /** Already for the full line/order (quantity multiplied in), capped by maxDiscountAmount and by the subtotal it's taken from. */
        BigDecimal amount
) {
}
