package kh.edu.istad.ite.features.order;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.features.cart.service.StorefrontCheckoutService;
import kh.edu.istad.ite.features.order.dto.*;
import kh.edu.istad.ite.features.order.service.OrderService;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;
import kh.edu.istad.ite.features.payment.dto.MarkReceiptPrintedRequest;
import kh.edu.istad.ite.features.payment.dto.ReceiptResponse;
import kh.edu.istad.ite.features.payment.service.ReceiptService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ReceiptService receiptService;
    private final StorefrontCheckoutService storefrontCheckoutService;

    @GetMapping
    public PageResponse<OrderResponse> findAllOrders(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return orderService.findAllOrders(businessId, pageable);
    }

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

    @GetMapping("/{orderId}/receipt")
    public ReceiptResponse findReceipt(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return receiptService.findByOrder(businessId, orderId);
    }

    @PatchMapping("/{orderId}/receipt/print")
    public ReceiptResponse markReceiptPrinted(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) MarkReceiptPrintedRequest request
    ) {
        return receiptService.markPrinted(businessId, orderId, request);
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return orderService.cancelOrder(businessId, orderId);
    }

    @PatchMapping("/{orderId}/confirm")
    public OrderResponse confirmOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return orderService.confirmOrder(businessId, orderId);
    }

    /** Business-owner action: approves a storefront Pay Later order, taking its stock off the shelf. */
    @PatchMapping("/{orderId}/pay-later/approve")
    public OrderResponse approvePayLaterOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId
    ) {
        return storefrontCheckoutService.approvePayLaterOrder(businessId, orderId);
    }

    @PostMapping("/filter")
    public PageResponse<OrderResponse> filterOrders(
            @PathVariable UUID businessId,
            @RequestBody RequestDto request,
            @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return orderService.filterOrders(businessId, request, pageable);
    }

    @PostMapping("/{orderId}/items")
    public OrderResponse addOrderItem(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @Valid @RequestBody AddOrderItemRequest request
    ) {
        return orderService.addOrderItem(businessId, orderId, request);
    }

    @PatchMapping("/{orderId}/items/{orderItemId}")
    public OrderResponse updateOrderItem(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId,
            @Valid @RequestBody UpdateOrderItemRequest request
    ) {
        return orderService.updateOrderItem(businessId, orderId, orderItemId, request);
    }

    @DeleteMapping("/{orderId}/items/{orderItemId}")
    public OrderResponse removeOrderItem(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId
    ) {
        return orderService.removeOrderItem(businessId, orderId, orderItemId);
    }

    @PatchMapping("/{orderId}/note")
    public OrderResponse updateOrderNote(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,

            @Valid @RequestBody UpdateOrderNoteRequest request
    ) {
        return orderService.updateOrderNote(businessId, orderId, request);
    }

    @PatchMapping("/{orderId}/customer")
    public OrderResponse updateOrderCustomer(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderCustomerRequest request
    ) {
        return orderService.updateOrderCustomer(businessId, orderId, request);
    }

    @PatchMapping("/{orderId}/discount")
    public OrderResponse updateOrderDiscount(
            @PathVariable UUID businessId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderDiscountRequest request
    ) {
        return orderService.updateOrderDiscount(businessId, orderId, request);
    }

    @PostMapping("/sync")
    public SyncOfflineOrdersResponse syncOfflineOrders(
            @PathVariable UUID businessId,
            @Valid @RequestBody SyncOfflineOrdersRequest request
    ) {
        return orderService.syncOfflineOrders(businessId, request);
    }
}