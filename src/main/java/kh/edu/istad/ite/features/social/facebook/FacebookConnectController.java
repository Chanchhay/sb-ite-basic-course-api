package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.config.props.FacebookProps;
import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.config.security.BusinessSecurityValidator;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.social.dto.FacebookPageSettingResponse;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.service.BusinessFacebookPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FacebookConnectController {

    private static final String SCOPES = "pages_show_list,pages_messaging,pages_manage_metadata,pages_read_engagement";

    private final FacebookProps facebookProps;
    private final StorefrontProps storefrontProps;
    private final FacebookGraphClient graphClient;
    private final BusinessFacebookPageService pageService;
    private final BusinessSecurityValidator businessSecurityValidator;
    private final BusinessRepository businessRepository;
    private final BusinessHelper businessHelper;

    private Business findMyBusiness() {
        return businessHelper.currentBusiness();
    }

    @GetMapping("/api/v1/businesses/social-settings/facebook")
    public ResponseEntity<FacebookPageSettingResponse> getMyFacebookPageSetting() {
        Business business = findMyBusiness();
        return pageService.findByBusinessId(business.getId())
                .map(page -> ResponseEntity.ok(toResponse(page, business)))
                .orElseGet(() -> ResponseEntity.ok(emptyResponse(business.getId())));
    }

    /** The old conversational text/button bot flow — independent of Mini App, same relationship as Telegram's isActive/isMiniAppEnabled pair. */
    @PatchMapping("/api/v1/businesses/social-settings/facebook/activate")
    public FacebookPageSettingResponse activate() {
        Business business = findMyBusiness();
        return toResponse(pageService.setActive(business.getId(), true), business);
    }

    @PatchMapping("/api/v1/businesses/social-settings/facebook/deactivate")
    public FacebookPageSettingResponse deactivate() {
        Business business = findMyBusiness();
        return toResponse(pageService.setActive(business.getId(), false), business);
    }

    @PatchMapping("/api/v1/businesses/social-settings/facebook/mini-app")
    public FacebookPageSettingResponse setMiniAppEnabled(@RequestParam boolean enabled) {
        Business business = findMyBusiness();
        return toResponse(pageService.setMiniAppEnabled(business.getId(), enabled), business);
    }

    /**
     * Takes the {@link Business} explicitly rather than reading {@code page.getBusiness()} —
     * that association is lazy, and by the time this DTO is built the transaction that
     * loaded {@code page} (inside the service layer) has already closed, so touching the
     * proxy here throws {@code LazyInitializationException}. Every caller already has a
     * fully-loaded {@code Business} in hand anyway.
     */
    private FacebookPageSettingResponse toResponse(BusinessFacebookPage page, Business business) {
        boolean miniAppEnabled = Boolean.TRUE.equals(page.getIsMiniAppEnabled());
        return new FacebookPageSettingResponse(
                page.getId(),
                business.getId(),
                page.getPageId(),
                page.getPageName(),
                true,
                Boolean.TRUE.equals(page.getIsActive()),
                page.getWelcomeMessage(),
                miniAppEnabled,
                miniAppEnabled ? storefrontProps.buildMessengerMiniAppUrl(business.getSlug()) : null
        );
    }

    private FacebookPageSettingResponse emptyResponse(UUID businessId) {
        return new FacebookPageSettingResponse(null, businessId, null, null, false, false, null, false, null);
    }

    @GetMapping("/api/v1/businesses/social-settings/facebook/connect-url")
    public Map<String, String> getMyConnectUrl() {
        UUID businessId = findMyBusiness().getId();
        String url = UriComponentsBuilder
                .fromUriString("https://www.facebook.com/" + facebookProps.getApiVersion() + "/dialog/oauth")
                .queryParam("client_id", facebookProps.getAppId())
                .queryParam("redirect_uri", facebookProps.getRedirectUri())
                .queryParam("state", businessId.toString())
                .queryParam("scope", SCOPES)
                .queryParam("response_type", "code")
                .build()
                .toUriString();

        return Map.of("url", url);
    }

    @DeleteMapping("/api/v1/businesses/social-settings/facebook")
    public ResponseEntity<Void> disconnectMyFacebookPage() {
        UUID businessId = findMyBusiness().getId();
        pageService.disconnectPage(businessId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/api/v1/businesses/{businessId}/social/facebook")
    public ResponseEntity<FacebookPageSettingResponse> getFacebookPageSetting(@PathVariable UUID businessId) {
        businessSecurityValidator.validateBusinessOwner(businessId);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));

        return pageService.findByBusinessId(businessId)
                .map(page -> ResponseEntity.ok(toResponse(page, business)))
                .orElseGet(() -> ResponseEntity.ok(emptyResponse(businessId)));
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/social/facebook")
    public ResponseEntity<Void> disconnectFacebookPage(@PathVariable UUID businessId) {
        businessSecurityValidator.validateBusinessOwner(businessId);
        pageService.disconnectPage(businessId);
        return ResponseEntity.noContent().build();
    }

    /** Called by the dashboard. Requires the caller to be that business's owner. */
    @GetMapping("/api/v1/businesses/{businessId}/social/facebook/connect-url")
    public Map<String, String> getConnectUrl(@PathVariable UUID businessId) {
        businessSecurityValidator.validateBusinessOwner(businessId);

        String url = UriComponentsBuilder
                .fromUriString("https://www.facebook.com/" + facebookProps.getApiVersion() + "/dialog/oauth")
                .queryParam("client_id", facebookProps.getAppId())
                .queryParam("redirect_uri", facebookProps.getRedirectUri())
                .queryParam("state", businessId.toString())
                .queryParam("scope", SCOPES)
                .queryParam("response_type", "code")
                .build()
                .toUriString();

        return Map.of("url", url);
    }

    /** Called by Facebook, not by the dashboard. Public — see SecurityConfig permitAll. */
    @GetMapping("/api/v1/social/facebook/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(name = "state", required = false) String businessIdRaw,
            @RequestParam(required = false) String error) {

        if (error != null) {
            log.warn("Facebook OAuth denied/cancelled: {}", error);
            return redirectToDashboard("facebook_denied");
        }
        if (code == null || businessIdRaw == null) {
            return redirectToDashboard("facebook_invalid_callback");
        }

        UUID businessId;
        try {
            businessId = UUID.fromString(businessIdRaw);
        } catch (IllegalArgumentException e) {
            return redirectToDashboard("facebook_invalid_state");
        }

        try {
            String shortLivedToken = graphClient.exchangeCodeForUserToken(
                    code, facebookProps.getRedirectUri(), facebookProps.getAppId(), facebookProps.getAppSecret());
            String longLivedToken = graphClient.exchangeForLongLivedUserToken(
                    shortLivedToken, facebookProps.getAppId(), facebookProps.getAppSecret());

            List<Map<String, Object>> pages = graphClient.fetchManagedPages(longLivedToken);
            if (pages.isEmpty()) {
                return redirectToDashboard("facebook_no_pages");
            }

            for (Map<String, Object> pageMap : pages) {
                String pageId = String.valueOf(pageMap.get("id"));
                String pageName = String.valueOf(pageMap.get("name"));
                String pageAccessToken = String.valueOf(pageMap.get("access_token"));
                log.info("Registering Facebook Page [{}] ({}) for business [{}]", pageName, pageId, businessId);
                pageService.registerPage(businessId, pageId, pageName, pageAccessToken);
            }

            return redirectToDashboard("facebook_connected");
        } catch (Exception e) {
            log.error("Facebook connect failed for business " + businessId, e);
            return redirectToDashboard("facebook_connect_failed");
        }
    }

    private ResponseEntity<Void> redirectToDashboard(String resultCode) {
        String base = facebookProps.getFrontendResultUrl();
        String location = (base == null || base.isBlank())
                ? "/?facebook=" + resultCode
                : base + (base.contains("?") ? "&" : "?") + "facebook=" + resultCode;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
