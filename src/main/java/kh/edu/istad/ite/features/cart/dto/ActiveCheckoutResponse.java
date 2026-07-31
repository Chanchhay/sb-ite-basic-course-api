package kh.edu.istad.ite.features.cart.dto;

public record ActiveCheckoutResponse(
        boolean hasPendingCheckout,
        StorefrontCheckoutResponse checkout
) {
}