package kh.edu.istad.ite.features.cart.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.cart.dto.ActiveCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutRequest;
import kh.edu.istad.ite.features.cart.dto.StorefrontCheckoutResponse;
import kh.edu.istad.ite.features.cart.dto.StorefrontPaymentStatusResponse;
import kh.edu.istad.ite.features.cart.service.StorefrontCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storefront/checkout")
@RequiredArgsConstructor
public class StorefrontCheckoutController {

    private final StorefrontCheckoutService checkoutService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public StorefrontCheckoutResponse createCheckout(
            @Valid @RequestBody StorefrontCheckoutRequest request
    ) {
        return checkoutService.createCheckout(request);
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
}