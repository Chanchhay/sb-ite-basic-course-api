package kh.edu.istad.ite.features.cart.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.cart.dto.ActiveCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutRequest;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontOrderResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontPaymentStatusResponse;
import kh.edu.istad.ite.features.cart.service.StorefrontCheckoutService;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storefront/checkout")
@RequiredArgsConstructor
public class StorefrontCheckoutController {

    private final StorefrontCheckoutService checkoutService;
    private final IdempotencyService idempotencyService;

    /**
     * Placing an order is the one request here that must not happen twice. A
     * double-tapped button or a retry after a dropped reply would otherwise leave
     * the shopper with two orders and the shop with two KHQR payments to reconcile,
     * so a repeat carrying the same {@code Idempotency-Key} is answered with the
     * first attempt's order rather than creating another. The header is optional:
     * a client that does not send one behaves exactly as before.
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public StorefrontCheckoutResponse createCheckout(
            @Valid @RequestBody StorefrontCheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return idempotencyService.execute(
                "storefront-checkout",
                AuthHelper.currentUserId().toString(),
                idempotencyKey,
                StorefrontCheckoutResponse.class,
                () -> checkoutService.createCheckout(request));
    }

    @GetMapping("/active")
    public ActiveCheckoutResponse findActiveCheckout() {
        return checkoutService.findActiveCheckout();
    }

    @GetMapping("/{orderId}/status")
    public StorefrontPaymentStatusResponse checkPaymentStatus(@PathVariable UUID orderId) {
        return checkoutService.checkPaymentStatus(orderId);
    }

    @PatchMapping("/{orderId}/cancel")
    public StorefrontPaymentStatusResponse cancelCheckout(@PathVariable UUID orderId) {
        return checkoutService.cancelCheckout(orderId);
    }

    @GetMapping("/history")
    public List<StorefrontOrderResponse> getMyOrders() {
        return checkoutService.getMyOrders();
    }

    @GetMapping("/{orderId}/receipt")
    public StorefrontOrderResponse getMyOrderReceipt(@PathVariable UUID orderId) {
        return checkoutService.getMyOrderReceipt(orderId);
    }
}