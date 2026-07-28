package kh.edu.istad.ite.shared.enums;

/**
 * How the discount value itself is expressed / applied.
 */
public enum DiscountType {
    /** value is a percentage (0-100) taken off the eligible amount. */
    PERCENTAGE,
    /** value is a fixed currency amount taken off the eligible amount. */
    FIXED_AMOUNT,
    /** "Buy X get Y" style promotion, driven by buy_quantity / get_quantity. */
    BUY_X_GET_Y
}
