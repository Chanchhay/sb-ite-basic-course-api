package kh.edu.istad.ite.features.cart.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;

import java.util.UUID;


public record StorefrontCheckoutRequest(
        @NotNull UUID businessId,
        String note,
        PaymentMethodType paymentMethod
) {
}