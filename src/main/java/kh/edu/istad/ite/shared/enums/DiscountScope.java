package kh.edu.istad.ite.shared.enums;

/**
 * What the discount is applied against.
 */
public enum DiscountScope {
    /** Applies to the whole order/cart total. */
    ORDER,
    /** Applies to specific product(s)/item(s). */
    PRODUCT,
    /** Applies to item(s) within a category. */
    CATEGORY,
    /** Applies only to customers holding a qualifying membership. */
    MEMBERSHIP
}
