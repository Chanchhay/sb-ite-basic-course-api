package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
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
    private final BusinessHelper businessHelper;

    @Override
    @Transactional
    public void handleUpdate(String webhookSecret, String secretTokenHeader, TelegramUpdate update) {
        BusinessTelegramBot setting = telegramBotRepository.findByWebhookSecret(webhookSecret).orElse(null);

        if (setting == null) {
            log.warn("Rejected Telegram webhook call: unknown webhookSecret in path");
            return;
        }

        if (secretTokenHeader == null || !secretTokenHeader.equals(setting.getWebhookSecret())) {
            log.warn("Rejected Telegram webhook call: secret_token header mismatch for business {}",
                    setting.getBusiness().getId());
            return;
        }

        if (!businessHelper.isFeatureEnabled(setting.getBusiness().getId(), BusinessFeature.TELEGRAM_BOT)) {
            log.info("Ignoring Telegram update: the platform disabled the bot for business {}",
                    setting.getBusiness().getId());
            return;
        }

        if (!Boolean.TRUE.equals(setting.getIsActive())) {
            log.info("Ignoring Telegram update for a deactivated bot (business {})", setting.getBusiness().getId());
            return;
        }

        if (update == null || update.message() == null || update.message().chat() == null) {
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
