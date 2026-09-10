package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.shared.enums.PaymentMethodType;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramAlertService {

    private final BusinessTelegramBotRepository telegramBotRepository;
    private final TelegramBotClient telegramBotClient;
    private final CredentialCipher credentialCipher;

    /** Kept for the one remaining direct KHQR-specific caller; delegates to the general alert as a DIGITAL payment. */
    public void sendQrPaymentAlert(Order order) {
        sendPaymentAlert(order, PaymentMethodType.DIGITAL);
    }

    /**
     * Fires for every way an order gets settled — cash, digital/QR, and pay
     * later alike — not just QR. This used to be QR-only, which meant a Pay
     * Later sale (or a cash sale) never notified the business's Telegram
     * chat at all, even though the same "someone just bought something"
     * event happened.
     */
    public void sendPaymentAlert(Order order, PaymentMethodType paymentMethod) {
        try {
            if (order == null || order.getBusiness() == null) {
                return;
            }

            Optional<BusinessTelegramBot> botSetting = telegramBotRepository.findByBusiness_Id(order.getBusiness().getId());
            if (botSetting.isEmpty()) {
                log.debug("No Telegram bot configured for business {}, skipping alert", order.getBusiness().getId());
                return;
            }

            String chatId = botSetting.get().getNotificationChatId();
            if (chatId == null || chatId.trim().isEmpty()) {
                log.debug("No notification chat id configured for business {}, skipping alert", order.getBusiness().getId());
                return;
            }

            String botToken = credentialCipher.decrypt(botSetting.get().getBotTokenEncrypted());

            boolean isPayLater = PaymentMethodType.PAY_LATER.equals(paymentMethod);
            String headline = switch (paymentMethod == null ? PaymentMethodType.DIGITAL : paymentMethod) {
                case CASH -> "🔔 *មានការលក់ថ្មី (Cash)!*";
                case DIGITAL -> "🔔 *មានការទូទាត់ប្រាក់ថ្មី (QR Payment)!*";
                case PAY_LATER -> "🔔 *មានការកម្មង់ថ្មី (Pay Later)!*";
            };

            StringBuilder message = new StringBuilder();
            message.append(headline).append("\n");
            message.append("━━━━━━━━━━━━━━━━━━━━\n");
            message.append("🏬 *ប្រភព (Channel):* ").append(order.getChannel() != null ? order.getChannel().name() : "N/A").append("\n");
            message.append("🧾 *វិក្កយបត្រ (Invoice):* `").append(order.getInvoiceNumber()).append("`\n");

            if (order.getCustomer() != null && order.getCustomer().getGlobalCustomer() != null) {
                String customerName = order.getCustomer().getGlobalCustomer().getFullName();
                if (customerName != null && !customerName.isEmpty()) {
                    message.append("👤 *អតិថិជន:* ").append(customerName).append("\n");
                }

                String phoneNumber = order.getCustomer().getGlobalCustomer().getPhoneNumber();
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    message.append("📞 *លេខទូរស័ព្ទ:* ").append(phoneNumber).append("\n");
                }
            }

            message.append("\n🛒 *ទំនិញដែលបានកុម្ម៉ង់:*\n");
            int count = 1;
            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    message.append(count++).append(". ").append(item.getItemName())
                            .append(" x ").append(item.getQuantity())
                            .append(" = $").append(item.getLineTotal()).append("\n");
                }
            }

            message.append("\n💵 *សរុប (Subtotal):* $").append(order.getSubtotal()).append("\n");
            message.append("🎟 *បញ្ចុះតម្លៃ (Discount):* $").append(order.getDiscountAmount()).append("\n");
            message.append("💰 *ប្រាក់ត្រូវបង់ (Total):* *$").append(order.getTotal()).append("*\n");
            message.append("━━━━━━━━━━━━━━━━━━━━\n");
            message.append(isPayLater
                    ? "⏳ អតិថិជននឹងទូទាត់នៅពេលក្រោយ — មិនទាន់ទទួលប្រាក់!"
                    : "✅ ការទូទាត់ទទួលបានជោគជ័យ!");

            telegramBotClient.sendMessage(botToken, Long.parseLong(chatId.trim()), message.toString());
            log.info("Sent Telegram payment alert ({}) for order {} to chat {}", paymentMethod, order.getId(), chatId);

        } catch (NumberFormatException e) {
            log.warn("Invalid notification chat id format for order {}", order.getId());
        } catch (Exception e) {
            log.warn("Failed to send Telegram payment alert for order {}: {}", order.getId(), e.getMessage());
        }
    }
}
