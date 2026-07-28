package kh.edu.istad.ite.shared.enums;

/**
 * The condition that must be met before a discount is eligible to apply.
 */
public enum DiscountRuleType {
    /** No extra condition, always eligible within its scope/schedule. */
    NO_CONDITION,
    /** Eligible once the cart/item quantity reaches min_quantity. */
    MIN_QUANTITY,
    /** Eligible once the order amount reaches min_order_amount. */
    MIN_ORDER_AMOUNT,
    /** Eligible based on buy_quantity / get_quantity ("buy X get Y"). */
    BUY_X_GET_Y,
    ORDER_TOTAL
}
