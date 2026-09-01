package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.LineDiscountApplication;
import kh.edu.istad.ite.features.discount.dto.OrderLineUnits;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place a discount's own condition (minQuantity, minOrderAmount, the
 * buy/get bundle size) is actually checked against what's known right now,
 * instead of every caller (browsing, add-to-cart, the cart display,
 * checkout) re-implementing — and subtly disagreeing on — the same lookup.
 * A discount that isn't eligible yet is simply absent from the result,
 * rather than granted and hoped to be corrected later.
 */
public interface DiscountApplicationService {

    /**
     * The best auto-applied (non-coupon), ITEM- or CATEGORY-scoped discount
     * for one line. ALL_ITEMS/ORDER-scoped discounts are deliberately
     * excluded here — {@link #resolveOrderDiscount} is the only place those
     * are evaluated, so a storewide discount is never also granted per line.
     *
     * @param unitPrice            the line's own undiscounted unit price
     * @param quantity             how many of this line — decides whether a
     *                             MIN_QUANTITY or BUY_X_GET_Y condition is met
     * @param orderSubtotalForCondition the running order subtotal to check a
     *                             MIN_ORDER_AMOUNT condition against, or
     *                             {@code null} when there is no cart yet to
     *                             check it against (browsing a single item
     *                             outside any cart) — a MIN_ORDER_AMOUNT
     *                             discount is never granted when this is
     *                             null, since there is nothing to verify it
     *                             with.
     */
    Optional<LineDiscountApplication> resolveLineDiscount(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal orderSubtotalForCondition
    );

    /**
     * The best auto-applied, ALL_ITEMS/ORDER-scoped discount, given every
     * line actually in the order. Line-level detail (not just a subtotal)
     * matters here specifically for BUY_X_GET_Y: "buy 2 get 1 free, any
     * item" spread across a mixed order has to free whichever units are
     * actually cheapest, not just divide the subtotal by a unit price that
     * doesn't exist when the order has more than one kind of item in it.
     */
    Optional<LineDiscountApplication> resolveOrderDiscount(
            UUID businessId,
            OrderChannel channel,
            List<OrderLineUnits> lines
    );

    /**
     * The name of the best discount that could apply to this item — a Buy 2
     * Get 1 shown on a product card while the shopper still has only one in
     * their cart, say — regardless of whether its condition (a minimum
     * quantity, a bundle size) is actually met right now. This is for
     * enticing a browsing shopper with what's on offer, never for pricing:
     * the amount only ever comes from {@link #resolveLineDiscount}, which
     * still refuses to grant anything whose condition isn't genuinely met.
     */
    Optional<String> previewDiscountLabel(
            UUID businessId,
            OrderChannel channel,
            UUID itemId,
            UUID itemGroupId
    );
}
