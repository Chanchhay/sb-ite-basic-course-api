package kh.edu.istad.ite.features.order;

import kh.edu.istad.ite.config.props.PublicApiProps;
import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.payment.abapay.AbaPayWayClient;
import kh.edu.istad.ite.features.payment.bakong.BakongTransactionClient;
import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.features.payment.khqr.QrImageRenderer;
import kh.edu.istad.ite.features.payment.repository.BusinessPaymentSettingRepository;
import kh.edu.istad.ite.features.payment.repository.PaymentQrCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/orders")
@RequiredArgsConstructor
@Slf4j
public class PublicOrderController {

    private final OrderRepository orderRepository;
    private final PaymentQrCodeRepository paymentQrCodeRepository;
    private final QrImageRenderer qrImageRenderer;
    private final AbaPayWayClient abaPayWayClient;
    private final BusinessPaymentSettingRepository paymentSettingRepository;
    private final BakongTransactionClient bakongTransactionClient;
    private final CredentialCipher credentialCipher;
    private final PublicApiProps publicApiProps;

    @GetMapping("/{orderId}/status")
    @Transactional(readOnly = true) // order.getBusiness() below is LAZY — without this
    // it throws LazyInitializationException the same
    // way the Facebook catalog bug did.
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<PaymentQrCode> qrCodeOpt = paymentQrCodeRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream().findFirst();

        String qrPayload = qrCodeOpt.map(PaymentQrCode::getQrPayload).orElse(null);
        String qrImageUri = qrPayload != null ? qrImageRenderer.toPngDataUri(qrPayload, 512) : null;

        String bakongDeepLink = resolveBakongDeepLink(order, qrPayload, orderId);

        String abapayDeeplink = abaPayWayClient
                .createAbapayDeeplink(
                        order.getId().toString(),
                        order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO,
                        order.getCurrency() != null ? order.getCurrency() : "USD",
                        publicApiProps.getBaseUrl() + "/api/v1/public/orders/" + orderId + "/status")
                .orElse(null); // no fake fallback scheme — an empty value lets the
        // frontend disable the ABA button instead of pretending
        // it will open an app that doesn't recognise the link

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

    /**
     * Turns the KHQR payload into a real link the Bakong app (and other
     * KHQR-participating bank apps) will actually open, via Bakong's own
     * generate_deeplink_by_qr endpoint — using the business's own saved
     * Bakong API token, the same one already used for payment status checks.
     */
    private String resolveBakongDeepLink(Order order, String qrPayload, UUID orderId) {
        if (!StringUtils.hasText(qrPayload)) {
            return null;
        }

        Optional<BusinessPaymentSetting> settingOpt =
                paymentSettingRepository.findByBusiness_Id(order.getBusiness().getId());

        if (settingOpt.isEmpty() || !StringUtils.hasText(settingOpt.get().getApiTokenEncrypted())) {
            log.warn("No Bakong API token saved for business {} — cannot generate a real app deeplink for order {}",
                    order.getBusiness().getId(), orderId);
            return null;
        }

        String accessToken = credentialCipher.decrypt(settingOpt.get().getApiTokenEncrypted());
        String appName = order.getBusiness().getDisplayName() != null
                ? order.getBusiness().getDisplayName()
                : "iPOS";

        return bakongTransactionClient
                .generateDeeplinkByQr(accessToken, qrPayload, null, appName)
                .orElse(null);
    }

}