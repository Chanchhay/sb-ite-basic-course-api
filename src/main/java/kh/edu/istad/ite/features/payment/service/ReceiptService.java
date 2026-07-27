package kh.edu.istad.ite.features.payment.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.payment.dto.MarkReceiptPrintedRequest;
import kh.edu.istad.ite.features.payment.dto.ReceiptResponse;
import kh.edu.istad.ite.shared.enums.ReceiptType;

import java.util.UUID;

public interface ReceiptService {

    void createForOrder(Business business, Order order, ReceiptType type);

    ReceiptResponse findByOrder(UUID businessId, UUID orderId);

    ReceiptResponse markPrinted(UUID businessId, UUID orderId, MarkReceiptPrintedRequest request);
}
