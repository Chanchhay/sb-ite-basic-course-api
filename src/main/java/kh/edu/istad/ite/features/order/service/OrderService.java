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
}
