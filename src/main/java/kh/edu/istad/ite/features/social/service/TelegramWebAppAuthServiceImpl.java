package kh.edu.istad.ite.features.social.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.CustomerChannelIdentity;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerChannelIdentityRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.social.dto.TelegramWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.TelegramWebAppAuthResponse;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TelegramInitDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramWebAppAuthServiceImpl implements TelegramWebAppAuthService {

    /** A captured initData older than this is refused even if the signature is still technically valid. */
    private static final long MAX_INIT_DATA_AGE_SECONDS = 24L * 60 * 60;

    private final BusinessRepository businessRepository;
    private final BusinessTelegramBotRepository telegramBotRepository;
    private final CredentialCipher credentialCipher;
    private final KeycloakBotAuthService keycloakBotAuthService;
    private final CustomerIdentityService customerIdentityService;
    private final CustomerChannelIdentityRepository customerChannelIdentityRepository;
    private final BusinessHelper businessHelper;
    private final MinioService minioService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public TelegramWebAppAuthResponse authenticate(TelegramWebAppAuthRequest request) {
        UUID businessId = parseBusinessId(request.businessId());
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store has not been found"));

        BusinessTelegramBot bot = telegramBotRepository.findByBusiness_Id(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This store has no Telegram bot connected"));

        // This endpoint is exclusively the Mini App's own login — gate it
        // on isMiniAppEnabled alone. isActive (the old text/reply-keyboard
        // flow) is a separate switch and must never decide whether the
        // Mini App itself is reachable, in either direction.
        if (!Boolean.TRUE.equals(bot.getIsMiniAppEnabled())
                || !businessHelper.isFeatureEnabled(businessId, BusinessFeature.TELEGRAM_BOT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Telegram ordering is not enabled for this store");
        }

        String botToken = credentialCipher.decrypt(bot.getBotTokenEncrypted());

        Map<String, String> verified;
        try {
            verified = TelegramInitDataValidator.verifyAndParse(request.initData(), botToken, MAX_INIT_DATA_AGE_SECONDS);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected Telegram Mini App auth for business {}: {}", businessId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Could not verify this Telegram session");
        }

        TelegramUser telegramUser = parseUser(verified.get("user"));

        // A fresh password each call, immediately traded for real tokens and
        // never stored anywhere — this is purely the mechanism for minting a
        // login for someone who authenticated via Telegram, not a credential
        // this account is meant to be reused with.
        String onceOffPassword = generateOnceOffPassword();

        KeycloakBotAuthService.KeycloakUserInfo userInfo = keycloakBotAuthService.findOrCreateTelegramKeycloakUser(
                telegramUser.id(), telegramUser.firstName(), telegramUser.lastName(),
                telegramUser.username(), null);

        boolean passwordSet = keycloakBotAuthService.setPassword(userInfo.id(), onceOffPassword);
        if (!passwordSet) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not sign you in right now");
        }

        KeycloakBotAuthService.TokenResponse tokens =
                keycloakBotAuthService.passwordGrantTokens(userInfo.username(), onceOffPassword);
        if (tokens == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not sign you in right now");
        }

        GlobalCustomer globalCustomer = customerIdentityService.resolve(
                CustomerIdentityService.parseKeycloakId(userInfo.id()),
                realEmailOnly(userInfo.email()),
                userInfo.phoneNumber(),
                userInfo.getFullName());

        Customer customer = customerIdentityService.customerFor(business, globalCustomer);
        linkChannelIdentity(business, customer, telegramUser);

        boolean profileComplete = StringUtils.hasText(globalCustomer.getEmail())
                && StringUtils.hasText(globalCustomer.getGender())
                && StringUtils.hasText(globalCustomer.getPhoneNumber())
                && StringUtils.hasText(customer.getAddress());

        return new TelegramWebAppAuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                business.getId(),
                business.getDisplayName(),
                business.getSlug(),
                toPublicUrl(business.getLogo()),
                customer.getId(),
                globalCustomer.getId(),
                telegramUser.id(),
                telegramUser.username(),
                userInfo.getFullName(),
                telegramUser.photoUrl(),
                globalCustomer.getPhoneNumber(),
                globalCustomer.getEmail(),
                globalCustomer.getGender(),
                customer.getAddress(),
                profileComplete
        );
    }

    private String realEmailOnly(String email) {
        if (email != null && email.endsWith("@telegram.fluxibiz")) {
            return null;
        }
        return email;
    }

    private void linkChannelIdentity(Business business, Customer customer, TelegramUser telegramUser) {
        String externalId = String.valueOf(telegramUser.id());
        customerChannelIdentityRepository
                .findByBusiness_IdAndChannelAndExternalId(
                        business.getId(), kh.edu.istad.ite.shared.enums.ChannelType.TELEGRAM, externalId)
                .orElseGet(() -> {
                    CustomerChannelIdentity identity = new CustomerChannelIdentity();
                    identity.setBusiness(business);
                    identity.setCustomer(customer);
                    identity.setChannel(kh.edu.istad.ite.shared.enums.ChannelType.TELEGRAM);
                    identity.setExternalId(externalId);
                    identity.setChannelUsername(telegramUser.username());
                    return customerChannelIdentityRepository.save(identity);
                });
    }

    private UUID parseBusinessId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessId must be a UUID");
        }
    }

    /** Telegram sends the user as a JSON string *value* inside the query-string-shaped initData, not as nested query params. */
    private TelegramUser parseUser(String userJson) {
        if (!StringUtils.hasText(userJson)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "initData carries no user");
        }
        try {
            JsonNode node = objectMapper.readTree(userJson);
            return new TelegramUser(
                    node.path("id").asLong(),
                    node.path("first_name").asText(null),
                    node.path("last_name").asText(null),
                    node.path("username").asText(null),
                    node.path("photo_url").asText(null)
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "initData's user field is malformed");
        }
    }

    private String generateOnceOffPassword() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toPublicUrl(String key) {
        if (!StringUtils.hasText(key)) return null;
        if (key.startsWith("http://") || key.startsWith("https://")) return key;
        return minioService.getPublicUrl(key);
    }

    private record TelegramUser(Long id, String firstName, String lastName, String username, String photoUrl) {
    }
}
