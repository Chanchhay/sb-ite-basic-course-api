package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;
import kh.edu.istad.ite.shared.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookServiceImpl implements TelegramWebhookService {

    private static final String STATE_IDLE = "IDLE";

    private final BusinessTelegramBotRepository telegramBotRepository;
    private final BotSessionRepository botSessionRepository;
    private final CredentialCipher credentialCipher;
    private final TelegramBotClient telegramBotClient;

    @Override
    @Transactional
    public void handleUpdate(String webhookSecret, String secretTokenHeader, TelegramUpdate update) {
        BusinessTelegramBot setting = telegramBotRepository.findByWebhookSecret(webhookSecret).orElse(null);

        if (setting == null) {
            log.warn("Rejected Telegram webhook call: unknown webhookSecret in path");
            return;
        }

        // Defense in depth: the path segment alone is guessable-ish (random but still a URL),
        // so we also require Telegram's own secret_token header to match it.
        if (secretTokenHeader == null || !secretTokenHeader.equals(setting.getWebhookSecret())) {
            log.warn("Rejected Telegram webhook call: secret_token header mismatch for business {}",
                    setting.getBusiness().getId());
            return;
        }

        if (!Boolean.TRUE.equals(setting.getIsActive())) {
            log.info("Ignoring Telegram update for a deactivated bot (business {})", setting.getBusiness().getId());
            return;
        }

        if (update == null || update.message() == null || update.message().chat() == null) {
            // Non-message updates (e.g. edited_message, callback_query) - nothing to reply to yet.
            return;
        }

        Long chatId = update.message().chat().id();
        String text = update.message().text() == null ? "" : update.message().text().trim();

        BotSession session = findOrCreateSession(setting, String.valueOf(chatId));
        session.setState(STATE_IDLE);
        session.setUpdatedAt(LocalDateTime.now());
        botSessionRepository.save(session);

        String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());
        String reply = buildReply(text, setting);

        telegramBotClient.sendMessage(botToken, chatId, reply);
    }

    private BotSession findOrCreateSession(BusinessTelegramBot setting, String chatId) {
        return botSessionRepository
                .findByBusiness_IdAndChannelAndExternalId(setting.getBusiness().getId(), ChannelType.TELEGRAM, chatId)
                .orElseGet(() -> {
                    BotSession created = new BotSession();
                    created.setBusiness(setting.getBusiness());
                    created.setChannel(ChannelType.TELEGRAM);
                    created.setExternalId(chatId);
                    created.setState(STATE_IDLE);
                    created.setContext(new HashMap<>());
                    return created;
                });
    }

    // MVP command handling - a real menu/cart/checkout flow driven by BotSession.state
    // and BotSession.context is a follow-up feature on top of this foundation.
    private String buildReply(String text, BusinessTelegramBot setting) {
        if ("/start".equalsIgnoreCase(text)) {
            return StringUtils.hasText(setting.getWelcomeMessage())
                    ? setting.getWelcomeMessage()
                    : "Welcome! Type /help to see what I can do.";
        }

        if ("/help".equalsIgnoreCase(text)) {
            return "Available commands:\n/start - greeting\n/help - show this help";
        }

        return "Sorry, I didn't understand that. Type /help to see available commands.";
    }
}
