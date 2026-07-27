package kh.edu.istad.ite.features.order;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.order.dto.*;
import kh.edu.istad.ite.features.order.service.OrderService;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public OrderResponse createOrder(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.createOrder(businessId, request);
    }

    @GetMapping("/{orderId}")
    public OrderResponse findOrderById(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return orderService.findOrderById(businessId, orderId);
    }

    @PostMapping("/{orderId}/khqr")
    public KhqrResponse generateKhqr(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return orderService.generateKhqr(businessId, orderId);
    }

    @PatchMapping("/{orderId}/pay")
    public SaleResponse payOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @Valid @RequestBody PayOrderRequest request
    ) {
        return orderService.payOrder(businessId, orderId, request);
    }

    @GetMapping("/{orderId}/payment-status")
    public PaymentStatusResponse checkPaymentStatus(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return orderService.checkPaymentStatus(businessId, orderId);
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return orderService.cancelOrder(businessId, orderId);
    }
}
