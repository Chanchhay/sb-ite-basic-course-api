package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
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

    public void sendQrPaymentAlert(Order order) {
        try {
            if (order == null || order.getBusiness() == null) {
                return;
            }

            Optional<BusinessTelegramBot> botSetting = telegramBotRepository.findByBusiness_Id(order.getBusiness().getId());
            if (botSetting.isEmpty() || !Boolean.TRUE.equals(botSetting.get().getIsActive())) {
                log.debug("No active Telegram bot for business {}, skipping alert", order.getBusiness().getId());
                return;
            }

            String chatId = botSetting.get().getNotificationChatId();
            if (chatId == null || chatId.trim().isEmpty()) {
                log.debug("No notification chat id configured for business {}, skipping alert", order.getBusiness().getId());
                return;
            }

            String botToken = credentialCipher.decrypt(botSetting.get().getBotTokenEncrypted());

            StringBuilder message = new StringBuilder();
            message.append("🔔 *មានការទូទាត់ប្រាក់ថ្មី (QR Payment)!*\n");
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
            message.append("✅ ការទូទាត់ទទួលបានជោគជ័យ!");

            telegramBotClient.sendMessage(botToken, Long.parseLong(chatId.trim()), message.toString());
            log.info("Sent Telegram payment alert for order {} to chat {}", order.getId(), chatId);

        } catch (NumberFormatException e) {
            log.warn("Invalid notification chat id format for order {}", order.getId());
        } catch (Exception e) {
            log.warn("Failed to send Telegram payment alert for order {}: {}", order.getId(), e.getMessage());
        }
    }
}
