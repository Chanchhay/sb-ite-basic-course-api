package kh.edu.istad.ite.features.cart.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.cart.dto.AddToCartRequest;
import kh.edu.istad.ite.features.cart.dto.CartCountResponse;
import kh.edu.istad.ite.features.cart.dto.CartSummaryResponse;
import kh.edu.istad.ite.features.cart.dto.UpdateCartItemRequest;
import kh.edu.istad.ite.features.cart.service.StorefrontCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storefront/cart")
@RequiredArgsConstructor
public class StorefrontCartController {

    private final StorefrontCartService cartService;

    @GetMapping
    public CartSummaryResponse findMyCart() {
        return cartService.findMyCart();
    }

    @GetMapping("/count")
    public CartCountResponse count() {
        return cartService.countMyCart();
    }

    @PostMapping("/items")
    public CartSummaryResponse addItem(@Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(request);
    }

    @PatchMapping("/items/{cartItemId}")
    public CartSummaryResponse updateItem(
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(cartItemId, request.quantity());
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartSummaryResponse removeItem(@PathVariable UUID cartItemId) {
        return cartService.removeItem(cartItemId);
    }

    @DeleteMapping("/stores/{businessId}")
    public CartSummaryResponse removeStore(@PathVariable UUID businessId) {
        return cartService.removeStore(businessId);
    }
}