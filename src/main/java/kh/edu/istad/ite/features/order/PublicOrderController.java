package kh.edu.istad.ite.features.order;

import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.payment.abapay.AbaPayWayClient;
import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.features.payment.khqr.QrImageRenderer;
import kh.edu.istad.ite.features.payment.repository.PaymentQrCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderRepository orderRepository;
    private final PaymentQrCodeRepository paymentQrCodeRepository;
    private final QrImageRenderer qrImageRenderer;
    private final AbaPayWayClient abaPayWayClient;

    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<PaymentQrCode> qrCodeOpt = paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream().findFirst();

        String qrPayload = qrCodeOpt.map(PaymentQrCode::getQrPayload).orElse(null);
        String qrImageUri = qrPayload != null ? qrImageRenderer.toPngDataUri(qrPayload, 512) : null;

        String bakongDeepLink = null;
        String abaCustomDeeplink = null;
        if (qrPayload != null && !qrPayload.isBlank()) {
            String encodedQr = URLEncoder.encode(qrPayload, StandardCharsets.UTF_8);
            bakongDeepLink = "bakong://qr?data=" + encodedQr;
            abaCustomDeeplink = "abamobile://qr?data=" + encodedQr;
        }

        String abapayDeeplink = abaPayWayClient
                .createAbapayDeeplink(
                        order.getId().toString(),
                        order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO,
                        order.getCurrency() != null ? order.getCurrency() : "USD",
                        "https://your-domain/api/v1/public/orders/" + orderId + "/status")
                .orElse(abaCustomDeeplink);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getId());
        response.put("invoiceNumber", order.getInvoiceNumber() != null ? order.getInvoiceNumber() : "");
        response.put("status", order.getStatus().name());
        response.put("subtotal", order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO);
        response.put("total", order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
        response.put("currency", order.getCurrency() != null ? order.getCurrency() : "USD");
        response.put("qrPayload", qrPayload != null ? qrPayload : "");
        response.put("qrImageUri", qrImageUri != null ? qrImageUri : "");
        response.put("bakongDeepLink", bakongDeepLink != null ? bakongDeepLink : "");
        response.put("abapayDeeplink", abapayDeeplink != null ? abapayDeeplink : "");

        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{orderId}/simulate-pay")
    public ResponseEntity<Map<String, Object>> simulatePayment(@PathVariable UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        order.setStatus(kh.edu.istad.ite.shared.enums.OrderStatus.PAID);
        orderRepository.save(order);

        paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId).forEach(qr -> {
            qr.setStatus(kh.edu.istad.ite.shared.enums.QrStatus.PAID);
            qr.setPaidAt(java.time.LocalDateTime.now());
            paymentQrCodeRepository.save(qr);
        });

        return ResponseEntity.ok(Map.of("message", "Simulated payment successful", "status", "PAID"));
    }

}