package kh.edu.istad.ite.features.social.facebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.config.props.FacebookProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
@Slf4j
public class FacebookGraphClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiVersion;

    @SuppressWarnings("removal")
    public FacebookGraphClient(FacebookProps props) {
        this.apiVersion = props.getApiVersion();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.parseMediaType("text/javascript"),
                MediaType.parseMediaType("text/javascript;charset=UTF-8"),
                MediaType.parseMediaType("text/plain")
        ));

        this.restClient = RestClient.builder()
                .baseUrl(props.getGraphBaseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, jacksonConverter))
                .build();
    }

    public void sendTextMessage(String pageId, String pageAccessToken, String psid, String text) {
        try {
            Map<String, Object> body = Map.of(
                    "recipient", Map.of("id", psid),
                    "message", Map.of("text", text),
                    "messaging_type", "RESPONSE"
            );

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Messenger message to PSID {}", psid);
        } catch (Exception e) {
            log.error("Failed to send Messenger message to PSID {}: {}", psid, e.getMessage());
            throw new RuntimeException("Failed to send message via Graph API", e);
        }
    }
    
    public void sendGenericTemplate(String pageId, String pageAccessToken, String psid,
                                    List<Map<String, Object>> elements) {
        try {
            Map<String, Object> payload = Map.of(
                    "template_type", "generic",
                    "elements", elements
            );
            Map<String, Object> attachment = Map.of(
                    "type", "template",
                    "payload", payload
            );
            Map<String, Object> body = Map.of(
                    "recipient", Map.of("id", psid),
                    "messaging_type", "RESPONSE",
                    "message", Map.of("attachment", attachment)
            );

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Messenger generic template ({} element(s)) to PSID {}", elements.size(), psid);
        } catch (Exception e) {
            log.error("Failed to send Messenger generic template to PSID {}: {}", psid, e.getMessage());
            throw new RuntimeException("Failed to send generic template via Graph API", e);
        }
    }


    public void sendButtonTemplate(String pageId, String pageAccessToken, String psid, String text,
                                   List<Map<String, Object>> buttons) {
        try {
            Map<String, Object> payload = Map.of(
                    "template_type", "button",
                    "text", text,
                    "buttons", buttons
            );
            Map<String, Object> attachment = Map.of(
                    "type", "template",
                    "payload", payload
            );
            Map<String, Object> body = Map.of(
                    "recipient", Map.of("id", psid),
                    "messaging_type", "RESPONSE",
                    "message", Map.of("attachment", attachment)
            );

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Messenger button template to PSID {}", psid);
        } catch (Exception e) {
            log.error("Failed to send Messenger button template to PSID {}: {}", psid, e.getMessage());
            throw new RuntimeException("Failed to send button template via Graph API", e);
        }
    }

    public void sendImage(String pageId, String pageAccessToken, String psid, byte[] imageBytes) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("recipient", "{\"id\":\"" + psid + "\"}");
            body.add("message", "{\"attachment\":{\"type\":\"image\", \"payload\":{\"is_reusable\":false}}}");

            ByteArrayResource contentsAsResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "khqr.png";
                }
            };
            body.add("filedata", contentsAsResource);

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Messenger image to PSID {}", psid);
        } catch (Exception e) {
            log.error("Failed to send Messenger image to PSID {}: {}", psid, e.getMessage());
            throw new RuntimeException("Failed to send image via Graph API", e);
        }
    }

    public void sendPdfAttachment(String pageId, String pageAccessToken, String psid, byte[] pdfBytes, String filename) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("recipient", "{\"id\":\"" + psid + "\"}");
            body.add("message", "{\"attachment\":{\"type\":\"file\", \"payload\":{\"is_reusable\":false}}}");

            ByteArrayResource pdfResource = new ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "Invoice-Receipt.pdf";
                }
            };
            body.add("filedata", pdfResource);

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Messenger PDF receipt to PSID {}", psid);
        } catch (Exception e) {
            log.error("Failed to send Messenger PDF receipt to PSID {}: {}", psid, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserProfile(String pageAccessToken, String psid) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{psid}")
                            .queryParam("fields", "first_name,last_name,profile_pic")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, psid))
                    .retrieve()
                    .body(Map.class);
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch user profile for PSID {}: {}", psid, e.getMessage());
            return Map.of("first_name", "Customer"); // Fallback
        }
    }



    /**
     * The Messenger equivalent of Telegram's persistent menu button — a
     * single web_url entry that opens the storefront webview, exactly like
     * the Mini App button. {@code messenger_extensions: true} is what makes
     * Facebook append a verifiable {@code signed_request} to the URL, which
     * {@code FacebookWebAppAuthService} needs to sign the visitor in.
     */
    public void setupMessengerProfile(String pageAccessToken, String miniAppUrl) {
        try {
            Map<String, Object> shopButton = new java.util.HashMap<>();
            shopButton.put("type", "web_url");
            shopButton.put("title", "🛍 បើកហាង");
            shopButton.put("url", miniAppUrl);
            shopButton.put("webview_height_ratio", "tall");
            shopButton.put("messenger_extensions", true);

            Map<String, Object> body = Map.of(
                    "get_started", Map.of("payload", "GET_STARTED"),
                    "persistent_menu", List.of(Map.of(
                            "locale", "default",
                            "composer_input_disabled", true,
                            "call_to_actions", List.of(shopButton)
                    ))
            );

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/me/messenger_profile")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            whitelistDomain(pageAccessToken, List.of(baseDomainOf(miniAppUrl)));

            log.info("Configured Messenger 'Get Started' button + Open Shop persistent menu");
        } catch (Exception e) {
            log.error("Failed to configure Messenger profile: {}", e.getMessage());
        }
    }

    private String baseDomainOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
            String host = uri.getHost();
            return scheme + "://" + (host != null ? host : url);
        } catch (Exception e) {
            return url;
        }
    }

    public void whitelistDomain(String pageAccessToken, List<String> domains) {
        whitelistDomainVerbose(pageAccessToken, domains);
    }

    /**
     * Same call, but returns exactly what Facebook said instead of swallowing
     * it. Reads the response as a {@code Map} rather than {@code String}/
     * {@code byte[]} — the custom Jackson converter registered on this client
     * (needed elsewhere for Facebook's occasional {@code text/javascript}
     * responses) claims that media type for every target type ahead of the
     * plain string/byte-array converters, but can only actually deserialize
     * the JSON *object* Facebook sends back into a real object type, not a
     * raw string or byte array — attempting either previously crashed here on
     * every call, masking whatever Facebook actually said.
     */
    @SuppressWarnings("unchecked")
    public String whitelistDomainVerbose(String pageAccessToken, List<String> domains) {
        try {
            Map<String, Object> body = Map.of(
                    "whitelisted_domains", domains
            );

            Map<String, Object> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/me/messenger_profile")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.info("Whitelisted domains for Messenger: {} -> {}", domains, response);
            return "OK: " + response;
        } catch (Exception e) {
            log.error("Failed to whitelist domains for Messenger: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    public String exchangeCodeForUserToken(String code, String redirectUri, String appId, String appSecret) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/oauth/access_token")
                            .queryParam("client_id", appId)
                            .queryParam("redirect_uri", redirectUri)
                            .queryParam("client_secret", appSecret)
                            .queryParam("code", code)
                            .build(apiVersion))
                    .retrieve()
                    .body(Map.class);
            return response == null ? null : (String) response.get("access_token");
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code for user token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange Facebook OAuth code", e);
        }
    }

    @SuppressWarnings("unchecked")
    public String exchangeForLongLivedUserToken(String shortLivedToken, String appId, String appSecret) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/oauth/access_token")
                            .queryParam("grant_type", "fb_exchange_token")
                            .queryParam("client_id", appId)
                            .queryParam("client_secret", appSecret)
                            .queryParam("fb_exchange_token", shortLivedToken)
                            .build(apiVersion))
                    .retrieve()
                    .body(Map.class);
            return response == null ? null : (String) response.get("access_token");
        } catch (Exception e) {
            log.error("Failed to exchange for long-lived user token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange Facebook long-lived token", e);
        }
    }

//    Step 3: list the Pages this user manages, each with its own (non-expiring) Page Access Token.
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchManagedPages(String userAccessToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/me/accounts")
                            .queryParam("access_token", userAccessToken)
                            .build(apiVersion))
                    .retrieve()
                    .body(Map.class);
            Object data = response == null ? null : response.get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch managed Pages: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch Facebook Pages", e);
        }
    }

//    Subscribes this app to a Page's webhook events, so messages start arriving at our webhook.
    public void subscribePageToWebhook(String pageId, String pageAccessToken) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/subscribed_apps")
                            .queryParam("subscribed_fields", "messages,messaging_postbacks")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Subscribed app to webhook events for Page {}", pageId);
        } catch (Exception e) {
            log.error("Failed to subscribe Page {} to webhook: {}", pageId, e.getMessage());
        }
    }
}
