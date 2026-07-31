package kh.edu.istad.ite.features.cart.service;

import kh.edu.istad.ite.features.cart.dto.ActiveCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutRequest;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontPaymentStatusResponse;

import java.util.UUID;


public interface StorefrontCheckoutService {

    StorefrontCheckoutResponse createCheckout(StorefrontCheckoutRequest request);

    ActiveCheckoutResponse findActiveCheckout();

    StorefrontPaymentStatusResponse checkPaymentStatus(UUID orderId);

    StorefrontPaymentStatusResponse cancelCheckout(UUID orderId);
}