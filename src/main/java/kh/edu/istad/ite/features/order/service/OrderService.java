package kh.edu.istad.ite.features.order.service;

import kh.edu.istad.ite.features.order.dto.*;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(UUID businessId, CreateOrderRequest request);

    OrderResponse findOrderById(UUID businessId, UUID orderId);

    KhqrResponse generateKhqr(UUID businessId, UUID orderId);

    SaleResponse payOrder(UUID businessId, UUID orderId, PayOrderRequest request);

    PaymentStatusResponse checkPaymentStatus(UUID businessId, UUID orderId);

    OrderResponse cancelOrder(UUID businessId, UUID orderId);

    OrderResponse confirmOrder(UUID businessId, UUID orderId);

    OrderResponse addOrderItem(UUID businessId, UUID orderId, AddOrderItemRequest request);
    OrderResponse updateOrderItem(UUID businessId, UUID orderId, UUID orderItemId, UpdateOrderItemRequest request);
    OrderResponse removeOrderItem(UUID businessId, UUID orderId, UUID orderItemId);
    
    OrderResponse updateOrderNote(UUID businessId, UUID orderId, UpdateOrderNoteRequest request);
    OrderResponse updateOrderDiscount(UUID businessId, UUID orderId, UpdateOrderDiscountRequest request);
    
    kh.edu.istad.ite.shared.dto.PageResponse<OrderResponse> filterOrders(UUID businessId, kh.edu.istad.ite.config.filter.RequestDto requestDto, org.springframework.data.domain.Pageable pageable);

    SyncOfflineOrdersResponse syncOfflineOrders(UUID businessId, SyncOfflineOrdersRequest request);
}
