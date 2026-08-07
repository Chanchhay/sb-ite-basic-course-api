package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.config.props.FacebookProps;
import kh.edu.istad.ite.config.security.BusinessSecurityValidator;
import kh.edu.istad.ite.features.social.service.BusinessFacebookPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
    private final FacebookGraphClient graphClient;
    private final BusinessFacebookPageService pageService;
    private final BusinessSecurityValidator businessSecurityValidator;

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

            // V1 simplification: BusinessFacebookPage is one-to-one, so we connect the first
            // Page returned. An owner who manages several Pages and wants a specific one is a
            // later improvement (would need a page-picker step before saving).
            Map<String, Object> firstPage = pages.get(0);
            String pageId = String.valueOf(firstPage.get("id"));
            String pageName = String.valueOf(firstPage.get("name"));
            String pageAccessToken = String.valueOf(firstPage.get("access_token"));

            pageService.registerPage(businessId, pageId, pageName, pageAccessToken);

            return redirectToDashboard("facebook_connected");
        } catch (Exception e) {
            log.error("Facebook connect failed for business {}: {}", businessId, e.getMessage());
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
