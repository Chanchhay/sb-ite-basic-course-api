package kh.edu.istad.ite.features.social.service;

import com.fasterxml.jackson.databind.JsonNode;
import kh.edu.istad.ite.config.props.FacebookProps;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.CustomerChannelIdentity;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerChannelIdentityRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthResponse;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.facebook.FacebookGraphClient;
import kh.edu.istad.ite.features.social.repository.BusinessFacebookPageRepository;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.helper.FacebookSignedRequestValidator;
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
public class FacebookWebAppAuthServiceImpl implements FacebookWebAppAuthService {

    private final BusinessRepository businessRepository;
    private final BusinessFacebookPageRepository facebookPageRepository;
    private final FacebookProps facebookProps;
    private final FacebookGraphClient graphClient;
    private final KeycloakBotAuthService keycloakBotAuthService;
    private final CustomerIdentityService customerIdentityService;
    private final CustomerChannelIdentityRepository customerChannelIdentityRepository;
    private final MinioService minioService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public FacebookWebAppAuthResponse authenticate(FacebookWebAppAuthRequest request) {
        UUID businessId = parseBusinessId(request.businessId());
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store has not been found"));

        BusinessFacebookPage page = facebookPageRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This store has no Facebook Page connected"));

        // This endpoint is exclusively the Mini App's own login — gate it on
        // isMiniAppEnabled alone. isActive (the text/button flow) is a
        // separate switch and must never decide whether the Mini App itself
        // is reachable, same reasoning as Telegram's auth gate.
        if (!Boolean.TRUE.equals(page.getIsMiniAppEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messenger ordering is not enabled for this store");
        }

        JsonNode payload;
        try {
            payload = FacebookSignedRequestValidator.verifyAndParse(request.signedRequest(), facebookProps.getAppSecret());
        } catch (IllegalArgumentException e) {
            log.warn("Rejected Messenger webview auth for business {}: {}", businessId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Could not verify this Messenger session");
        }

        String psid = payload.path("psid").asText(null);
        if (!StringUtils.hasText(psid)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "signed_request carries no psid");
        }

        Map<String, Object> profile = graphClient.getUserProfile(page.getPageAccessTokenEncrypted(), psid);
        String firstName = profile.get("first_name") != null ? String.valueOf(profile.get("first_name")) : "Facebook";
        String lastName = profile.get("last_name") != null ? String.valueOf(profile.get("last_name")) : "User";

        String onceOffPassword = generateOnceOffPassword();

        KeycloakBotAuthService.KeycloakUserInfo userInfo =
                keycloakBotAuthService.findOrCreateFacebookKeycloakUser(psid, firstName, lastName);

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
                null,
                userInfo.phoneNumber(),
                userInfo.getFullName());

        Customer customer = customerIdentityService.customerFor(business, globalCustomer);
        linkChannelIdentity(business, customer, psid);

        boolean profileComplete = StringUtils.hasText(globalCustomer.getEmail())
                && StringUtils.hasText(globalCustomer.getGender())
                && StringUtils.hasText(globalCustomer.getPhoneNumber())
                && StringUtils.hasText(customer.getAddress());

        return new FacebookWebAppAuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                business.getId(),
                business.getDisplayName(),
                business.getSlug(),
                toPublicUrl(business.getLogo()),
                customer.getId(),
                globalCustomer.getId(),
                psid,
                userInfo.getFullName(),
                globalCustomer.getPhoneNumber(),
                globalCustomer.getEmail(),
                globalCustomer.getGender(),
                customer.getAddress(),
                profileComplete
        );
    }

    private void linkChannelIdentity(Business business, Customer customer, String psid) {
        customerChannelIdentityRepository
                .findByBusiness_IdAndChannelAndExternalId(business.getId(), ChannelType.MESSENGER, psid)
                .orElseGet(() -> {
                    CustomerChannelIdentity identity = new CustomerChannelIdentity();
                    identity.setBusiness(business);
                    identity.setCustomer(customer);
                    identity.setChannel(ChannelType.MESSENGER);
                    identity.setExternalId(psid);
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
}
