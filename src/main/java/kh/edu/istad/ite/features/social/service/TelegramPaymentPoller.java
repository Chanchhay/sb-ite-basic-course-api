package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.customer.repository.CustomerChannelIdentityRepository;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.features.payment.repository.PaymentQrCodeRepository;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.InlineKeyboardButton;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.QrStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Watches every outstanding Telegram KHQR and confirms it against Bakong without
 * the customer having to tap anything. When Bakong reports the transfer, the order
 * settles and the confirmation is pushed straight into the chat.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramPaymentPoller {

    private final PaymentQrCodeRepository paymentQrCodeRepository;
    private final BusinessTelegramBotRepository telegramBotRepository;
    private final CustomerChannelIdentityRepository customerChannelIdentityRepository;
    private final TelegramCheckoutService telegramCheckoutService;
    private final TelegramBotClient telegramBotClient;
    private final CredentialCipher credentialCipher;

    @Scheduled(
            initialDelayString = "${app.telegram.payment-poll-initial-delay-ms:10000}",
            fixedDelayString = "${app.telegram.payment-poll-interval-ms:5000}")
    public void pollOutstandingPayments() {
        List<PaymentQrCode> outstanding;

        try {
            outstanding = paymentQrCodeRepository.findOutstandingByChannel(
                    QrStatus.PENDING, OrderStatus.PENDING, OrderChannel.TELEGRAM);
        } catch (Exception exception) {
            log.error("Could not load outstanding Telegram QR codes: {}", exception.getMessage());
            return;
        }

        if (outstanding.isEmpty()) {
            return;
        }

        log.debug("Polling Bakong for {} outstanding Telegram payment(s)", outstanding.size());

        for (PaymentQrCode qrCode : outstanding) {
            try {
                process(qrCode);
            } catch (Exception exception) {
                // One bad order must never stop the loop for everybody else.
                log.warn("Polling failed for QR {}: {}", qrCode.getId(), exception.getMessage());
            }
        }
    }

    private void process(PaymentQrCode qrCode) {
        Order order = qrCode.getOrder();
        UUID businessId = qrCode.getBusiness().getId();

        TelegramCheckoutService.VerifyResult result;
        try {

            result = telegramCheckoutService.verifyAndSettle(businessId, order.getId());
        } catch (TelegramCheckoutException exception) {
            log.debug("Cannot verify Telegram order {} yet: {}", order.getId(), exception.getMessage());
            return;
        }

        if (result.paid()) {
            notifyPaid(businessId, order, result.invoiceNumber());
        } else if (result.expired()) {
            notifyExpired(businessId, order);
        }
    }

    private void notifyPaid(UUID businessId, Order order, String invoiceNumber) {
        send(businessId, order,
                "🎉 *ការទូទាត់ជោគជ័យ!*\n"
                        + "━━━━━━━━━━━━━━━━━━━━\n"
                        + "🧾 លេខវិក្កយបត្រ ៖ `" + invoiceNumber + "`\n"
                        + "✅ ប្រព័ន្ធបានទទួលប្រាក់របស់អ្នករួចរាល់។\n\n"
                        + "អរគុណសម្រាប់ការបញ្ជាទិញ! ហាងកំពុងរៀបចំទំនិញជូនអ្នក។",
                List.of(List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog")),
                        List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));

        log.info("Auto-confirmed Telegram order {} ({})", order.getId(), invoiceNumber);
    }

    private void notifyExpired(UUID businessId, Order order) {
        send(businessId, order,
                "⏰ *កូដ KHQR បានផុតកំណត់*\n"
                        + "━━━━━━━━━━━━━━━━━━━━\n"
                        + "🧾 លេខវិក្កយបត្រ ៖ `" + order.getInvoiceNumber() + "`\n\n"
                        + "ទំនិញនៅតែរក្សាទុកក្នុងកន្ត្រករបស់អ្នក។ សូមចុច គិតលុយ ដើម្បីបង្កើតកូដថ្មី។",
                List.of(List.of(new InlineKeyboardButton("🛒 មើលកន្ត្រក", "menu:cart")),
                        List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
    }

    private void send(UUID businessId, Order order, String message,
                      List<List<InlineKeyboardButton>> keyboard) {
        if (order.getCustomer() == null) {
            return;
        }

        Optional<BusinessTelegramBot> botSetting = telegramBotRepository.findByBusiness_Id(businessId);

        if (botSetting.isEmpty() || !Boolean.TRUE.equals(botSetting.get().getIsActive())) {
            log.debug("No active Telegram bot for business {}, skipping notification", businessId);
            return;
        }

        Optional<String> chatId = customerChannelIdentityRepository
                .findByBusiness_IdAndChannelAndCustomer_Id(
                        businessId, ChannelType.TELEGRAM, order.getCustomer().getId())
                .map(identity -> identity.getExternalId());

        if (chatId.isEmpty()) {
            log.warn("No Telegram identity for customer {} in business {}, cannot notify",
                    order.getCustomer().getId(), businessId);
            return;
        }

        try {
            String botToken = credentialCipher.decrypt(botSetting.get().getBotTokenEncrypted());
            telegramBotClient.sendMessage(botToken, Long.parseLong(chatId.get()), message, keyboard);
        } catch (NumberFormatException exception) {
            log.warn("Telegram external id {} is not a numeric chat id", chatId.get());
        } catch (Exception exception) {
            log.warn("Could not deliver the Telegram notification: {}", exception.getMessage());
        }
    }
}