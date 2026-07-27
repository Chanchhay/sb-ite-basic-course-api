package kh.edu.istad.ite.features.payment.service;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.payment.dto.MarkReceiptPrintedRequest;
import kh.edu.istad.ite.features.payment.dto.ReceiptResponse;
import kh.edu.istad.ite.features.payment.entity.Receipt;
import kh.edu.istad.ite.features.payment.repository.ReceiptRepository;
import kh.edu.istad.ite.shared.enums.ReceiptType;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;

    @Override
    @Transactional
    public void createForOrder(Business business, Order order, ReceiptType type) {
        if (receiptRepository.existsByOrder_Id(order.getId())) {
            return;
        }

        Receipt receipt = new Receipt();
        receipt.setBusiness(business);
        receipt.setOrder(order);
        receipt.setType(type);
        receipt.setInvoiceNumber(order.getInvoiceNumber());

        receiptRepository.save(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse findByOrder(UUID businessId, UUID orderId) {
        return toResponse(findReceipt(businessId, orderId));
    }

    @Override
    @Transactional
    public ReceiptResponse markPrinted(UUID businessId, UUID orderId, MarkReceiptPrintedRequest request) {
        Receipt receipt = findReceipt(businessId, orderId);

        receipt.setPrintedBy(AuthHelper.currentUserId());
        receipt.setPrintedAt(LocalDateTime.now());
        if (request != null) {
            receipt.setDeviceId(request.deviceId());
        }

        return toResponse(receiptRepository.save(receipt));
    }

    private Receipt findReceipt(UUID businessId, UUID orderId) {
        return receiptRepository.findByOrder_IdAndBusiness_Id(orderId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No receipt has been issued for this order yet"));
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getOrder().getId(),
                receipt.getInvoiceNumber(),
                receipt.getVatNumber(),
                receipt.getType(),
                receipt.getFileUrl(),
                receipt.getDeviceId(),
                receipt.getPrintedBy(),
                receipt.getPrintedAt(),
                receipt.getCreatedDate()
        );
    }
}
