package kh.edu.istad.ite.features.cart.service;

import kh.edu.istad.ite.features.cart.dto.ActiveCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutRequest;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontOrderResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontPaymentStatusResponse;
import kh.edu.istad.ite.features.order.dto.OrderResponse;

import java.util.List;
import java.util.UUID;


public interface StorefrontCheckoutService {

    StorefrontCheckoutResponse createCheckout(StorefrontCheckoutRequest request);

    /** Business-owner action: approves a pending Pay Later order, taking its stock off the shelf. */
    OrderResponse approvePayLaterOrder(UUID businessId, UUID orderId);

    ActiveCheckoutResponse findActiveCheckout();

    StorefrontPaymentStatusResponse checkPaymentStatus(UUID orderId);

    StorefrontPaymentStatusResponse cancelCheckout(UUID orderId);

    List<StorefrontOrderResponse> getMyOrders();

    StorefrontOrderResponse getMyOrderReceipt(UUID orderId);
}