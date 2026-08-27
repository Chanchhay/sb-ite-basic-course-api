package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.features.payment.repository.PaymentQrCodeRepository;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.social.repository.BusinessFacebookPageRepository;
import kh.edu.istad.ite.features.social.service.TelegramAlertService;
import kh.edu.istad.ite.features.social.event.FacebookQrGeneratedEvent;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.QrStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class FacebookPaymentPoller {

    private final PaymentQrCodeRepository paymentQrCodeRepository;
    private final BusinessFacebookPageRepository pageRepository;
    private final BotSessionRepository botSessionRepository;
    private final FacebookCheckoutService facebookCheckoutService;
    private final FacebookGraphClient graphClient;
    private final TelegramAlertService telegramAlertService;

    private final AtomicBoolean hasPendingQr = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            List<PaymentQrCode> outstanding = paymentQrCodeRepository.findOutstandingByChannel(
                    QrStatus.PENDING, OrderStatus.PENDING, OrderChannel.MESSENGER);
            if (!outstanding.isEmpty()) {
                hasPendingQr.set(true);
                log.info("Startup check found {} outstanding Messenger payment(s), active polling enabled", outstanding.size());
            }
        } catch (Exception exception) {
            log.warn("Could not check outstanding Messenger QR codes on startup: {}", exception.getMessage());
        }
    }

    @EventListener
    public void onFacebookQrGenerated(FacebookQrGeneratedEvent event) {
        if (!hasPendingQr.getAndSet(true)) {
            log.info("Messenger QR code generated: enabling payment polling");
        }
    }

    @Scheduled(
            initialDelayString = "${app.telegram.payment-poll-initial-delay-ms:10000}",
            fixedDelayString = "${app.telegram.payment-poll-interval-ms:10000}")
    public void pollOutstandingPayments() {
        if (!hasPendingQr.get()) {
            return;
        }

        List<PaymentQrCode> outstanding;
        try {
            outstanding = paymentQrCodeRepository.findOutstandingByChannel(
                    QrStatus.PENDING, OrderStatus.PENDING, OrderChannel.MESSENGER);
        } catch (Exception exception) {
            log.error("Could not load outstanding Messenger QR codes: {}", exception.getMessage());
            return;
        }

        if (outstanding.isEmpty()) {
            if (hasPendingQr.compareAndSet(true, false)) {
                log.info("No outstanding Messenger QR codes remaining: pausing payment polling");
            }
            return;
        }

        log.debug("Polling Bakong for {} outstanding Messenger payment(s)", outstanding.size());

        for (PaymentQrCode qrCode : outstanding) {
            try {
                process(qrCode);
            } catch (Exception exception) {
                log.warn("Polling failed for QR {}: {}", qrCode.getId(), exception.getMessage());
            }
        }
    }

    private void process(PaymentQrCode qrCode) {
        Order order = qrCode.getOrder();
        UUID businessId = qrCode.getBusiness().getId();

        FacebookCheckoutService.VerifyResult result;
        try {
            result = facebookCheckoutService.verifyAndSettle(businessId, order.getId());
        } catch (Exception exception) {
            log.debug("Cannot verify Messenger order {} yet: {}", order.getId(), exception.getMessage());
            return;
        }

        if (result.paid()) {
            notifyPaid(businessId, order, result.invoiceNumber(), result.receiptText());
        } else if (result.expired()) {
            notifyExpired(businessId, order);
        }
    }

    private void notifyPaid(UUID businessId, Order order, String invoiceNumber, String receiptText) {
        String message = "🎉 ការទូទាត់ជោគជ័យ!\n"
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "🧾 លេខវិក្កយបត្រ ៖ " + invoiceNumber + "\n"
                + "✅ ប្រព័ន្ធបានទទួលប្រាក់របស់អ្នករួចរាល់។\n\n"
                + "អរគុណសម្រាប់ការបញ្ជាទិញ! ហាងកំពុងរៀបចំទំនិញជូនអ្នក។";

        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🗂️ មើលផលិតផល", "payload", "CATALOG"),
                Map.of("type", "postback", "title", "📝 ប្រវត្តិបញ្ជាទិញ", "payload", "ORDER_HISTORY"),
                Map.of("type", "postback", "title", "🛒 មើលកន្ត្រក", "payload", "CART_VIEW")
        );

        send(businessId, order, message, buttons);

        if (receiptText != null) {
            send(businessId, order, receiptText, null);
        }

        log.info("Auto-confirmed Messenger order {} ({})", order.getId(), invoiceNumber);
        telegramAlertService.sendQrPaymentAlert(order);
    }

    private void notifyExpired(UUID businessId, Order order) {
        String message = "⏰ កូដ KHQR បានផុតកំណត់\n"
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "🧾 លេខវិក្កយបត្រ ៖ " + order.getInvoiceNumber() + "\n\n"
                + "ទំនិញនៅតែរក្សាទុកក្នុងកន្ត្រករបស់អ្នក។ សូមចុច គិតលុយ ដើម្បីបង្កើតកូដថ្មី។";

        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🛒 មើលកន្ត្រក", "payload", "CART_VIEW")
        );

        send(businessId, order, message, buttons);
    }

    private void send(UUID businessId, Order order, String text, List<Map<String, Object>> buttons) {
        if (order.getCustomer() == null) return;

        Optional<BusinessFacebookPage> pageOpt = pageRepository.findByBusinessId(businessId);
        if (pageOpt.isEmpty() || !Boolean.TRUE.equals(pageOpt.get().getIsActive())) {
            log.debug("No active Facebook Page for business {}, skipping notification", businessId);
            return;
        }
        BusinessFacebookPage page = pageOpt.get();

        Optional<BotSession> sessionOpt = botSessionRepository.findByBusiness_IdAndChannelAndCustomer_Id(
                businessId, ChannelType.MESSENGER, order.getCustomer().getId());

        if (sessionOpt.isEmpty()) {
            log.warn("No BotSession for customer {} in business {}, cannot notify via Messenger",
                    order.getCustomer().getId(), businessId);
            return;
        }

        String psid = sessionOpt.get().getExternalId();

        try {
            if (buttons != null && !buttons.isEmpty()) {
                graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text, buttons);
            } else {
                graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text);
            }
        } catch (Exception exception) {
            log.warn("Could not deliver the Messenger notification: {}", exception.getMessage());
        }
    }
}
