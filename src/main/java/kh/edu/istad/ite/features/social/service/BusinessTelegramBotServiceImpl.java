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

        telegramBotClient.deleteWebhook(botToken);
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
            telegramBotClient.setChatMenuButton(botToken, buildMiniAppUrl(setting.getBusiness().getSlug()), "🛍 Open Shop");
        } else {
            telegramBotClient.resetChatMenuButton(botToken);
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

    /**
     * Always the path-based storefront route, regardless of whether
     * subdomains are enabled — this is the actual Next.js route the Mini
     * App pages live at ({@code /store/[slug]}), and building it explicitly
     * avoids depending on whatever a subdomain might rewrite to internally.
     */
    private String buildMiniAppUrl(String slug) {
        return storefrontProps.getProtocol() + "://" + storefrontProps.getBaseDomain()
                + storefrontProps.getPathPrefix() + "/" + slug + "?tma=true";
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
                miniAppEnabled ? buildMiniAppUrl(setting.getBusiness().getSlug()) : null
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
