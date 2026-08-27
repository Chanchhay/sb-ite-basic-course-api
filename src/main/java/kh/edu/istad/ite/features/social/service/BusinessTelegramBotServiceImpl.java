package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.config.props.TelegramProps;
import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingRequest;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingResponse;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.features.social.telegram.TelegramBotIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessTelegramBotServiceImpl implements BusinessTelegramBotService {

    private final BusinessRepository businessRepository;
    private final BusinessHelper businessHelper;
    private final BusinessTelegramBotRepository telegramBotRepository;
    private final CredentialCipher credentialCipher;
    private final TelegramBotClient telegramBotClient;
    private final TelegramProps telegramProps;
    private final StorefrontProps storefrontProps;

    @Override
    @Transactional(readOnly = true)
    public TelegramBotSettingResponse getMySetting() {
        return toResponse(findMySetting());
    }

    @Override
    @Transactional
    public TelegramBotSettingResponse connect(TelegramBotSettingRequest request) {
        requireWebhookBaseUrlConfigured();

        Business business = findMyBusiness();
        String botToken = request.botToken().trim();

        // Validate the token with Telegram itself and pull the bot's real id/username -
        // never trust owner-entered metadata, only the token.
        TelegramBotIdentity identity = telegramBotClient.getMe(botToken);

        BusinessTelegramBot setting = telegramBotRepository.findByBusiness_Id(business.getId())
                .orElseGet(() -> {
                    BusinessTelegramBot created = new BusinessTelegramBot();
                    created.setBusiness(business);
                    created.setIsActive(false);
                    created.setWebhookSecret(UUID.randomUUID().toString().replace("-", ""));
                    return created;
                });

        setting.setBotTokenEncrypted(credentialCipher.encrypt(botToken));
        setting.setTelegramBotId(identity.id());
        setting.setBotUsername(identity.username());
        setting.setWelcomeMessage(trimToNull(request.welcomeMessage()));
        setting.setNotificationChatId(trimToNull(request.notificationChatId()));

        telegramBotClient.setWebhook(botToken, buildWebhookUrl(setting.getWebhookSecret()), setting.getWebhookSecret());
        setting.setIsActive(true);

        return toResponse(telegramBotRepository.save(setting));
    }

    @Override
    @Transactional
    public TelegramBotSettingResponse activate() {
        requireWebhookBaseUrlConfigured();

        BusinessTelegramBot setting = findMySetting();
        String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());

        telegramBotClient.setWebhook(botToken, buildWebhookUrl(setting.getWebhookSecret()), setting.getWebhookSecret());
        setting.setIsActive(true);

        return toResponse(telegramBotRepository.save(setting));
    }

    @Override
    @Transactional
    public TelegramBotSettingResponse deactivate() {
        BusinessTelegramBot setting = findMySetting();
        String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());

        // The webhook is what lets the bot reply with the "Open Shop"
        // button in group chats at all (unlike the persistent menu button,
        // which works in private chats without it). Turning off the old
        // text flow shouldn't also break Mini App there if it's still on.
        if (!Boolean.TRUE.equals(setting.getIsMiniAppEnabled())) {
            telegramBotClient.deleteWebhook(botToken);
        }
        setting.setIsActive(false);

        return toResponse(telegramBotRepository.save(setting));
    }

    @Override
    @Transactional
    public void disconnect() {
        BusinessTelegramBot setting = findMySetting();
        String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());

        telegramBotClient.deleteWebhook(botToken);
        if (Boolean.TRUE.equals(setting.getIsMiniAppEnabled())) {
            telegramBotClient.resetChatMenuButton(botToken);
        }
        telegramBotRepository.delete(setting);
    }

    @Override
    @Transactional
    public TelegramBotSettingResponse setMiniAppEnabled(boolean enabled) {
        BusinessTelegramBot setting = findMySetting();
        String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());

        if (enabled) {
            telegramBotClient.setChatMenuButton(botToken, storefrontProps.buildMiniAppUrl(setting.getBusiness().getSlug()), "🛍 Open Shop");
            // Group chats need a live webhook to reply with the "Open
            // Shop" inline button — make sure one exists even if the old
            // text flow (isActive) was left off, since Mini App is
            // independent of it.
            if (!Boolean.TRUE.equals(setting.getIsActive())) {
                requireWebhookBaseUrlConfigured();
                telegramBotClient.setWebhook(botToken, buildWebhookUrl(setting.getWebhookSecret()), setting.getWebhookSecret());
            }
        } else {
            telegramBotClient.resetChatMenuButton(botToken);
            // Neither mode needs the webhook anymore — tear it down the
            // same way deactivate() would, rather than leaving a stale
            // subscription Telegram keeps calling for no reason.
            if (!Boolean.TRUE.equals(setting.getIsActive())) {
                telegramBotClient.deleteWebhook(botToken);
            }
        }
        setting.setIsMiniAppEnabled(enabled);

        return toResponse(telegramBotRepository.save(setting));
    }

    private BusinessTelegramBot findMySetting() {
        return telegramBotRepository.findByBusiness_Id(findMyBusiness().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Telegram bot has not been configured"));
    }

    private Business findMyBusiness() {
        return businessHelper.currentBusiness();
    }

    private void requireWebhookBaseUrlConfigured() {
        if (!StringUtils.hasText(telegramProps.getWebhookBaseUrl())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Telegram integration is not configured on this platform yet");
        }
    }

    private String buildWebhookUrl(String webhookSecret) {
        return telegramProps.getWebhookBaseUrl() + "/api/v1/webhooks/telegram/" + webhookSecret;
    }

    private TelegramBotSettingResponse toResponse(BusinessTelegramBot setting) {
        boolean miniAppEnabled = Boolean.TRUE.equals(setting.getIsMiniAppEnabled());
        return new TelegramBotSettingResponse(
                setting.getId(),
                setting.getBusiness().getId(),
                setting.getBotUsername(),
                setting.getTelegramBotId(),
                setting.getWelcomeMessage(),
                StringUtils.hasText(setting.getBotTokenEncrypted()),
                Boolean.TRUE.equals(setting.getIsActive()),
                buildWebhookUrl(setting.getWebhookSecret()),
                setting.getNotificationChatId(),
                miniAppEnabled,
                miniAppEnabled ? storefrontProps.buildMiniAppUrl(setting.getBusiness().getSlug()) : null
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
