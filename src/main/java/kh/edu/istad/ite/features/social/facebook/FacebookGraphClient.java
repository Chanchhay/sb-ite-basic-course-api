package kh.edu.istad.ite.features.social.facebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.config.props.FacebookProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.MultipartBodyBuilder;

@Component
@Slf4j
public class FacebookGraphClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiVersion;

    public FacebookGraphClient(FacebookProps props) {
        this.apiVersion = props.getApiVersion();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(props.getGraphBaseUrl())
                .requestFactory(requestFactory)
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
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("recipient", "{\"id\":\"" + psid + "\"}").contentType(MediaType.APPLICATION_JSON);
            bodyBuilder.part("message", "{\"attachment\":{\"type\":\"image\", \"payload\":{\"is_reusable\":false}}}")
                    .contentType(MediaType.APPLICATION_JSON);
            bodyBuilder.part("filedata", imageBytes, MediaType.IMAGE_PNG).filename("khqr.png");

            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{apiVersion}/{pageId}/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build(apiVersion, pageId))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(bodyBuilder.build())
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Messenger image to PSID {}", psid);
        } catch (Exception e) {
            log.error("Failed to send Messenger image to PSID {}: {}", psid, e.getMessage());
            throw new RuntimeException("Failed to send image via Graph API", e);
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



    public void setupMessengerProfile(String pageAccessToken) {
        try {
            Map<String, Object> body = Map.of(
                    "get_started", Map.of("payload", "GET_STARTED"),
                    "persistent_menu", List.of(Map.of(
                            "locale", "default",
                            "composer_input_disabled", false,
                            "call_to_actions", List.of(
                                    Map.of("type", "postback", "title", "🗂️ មើលផលិតផល", "payload", "CATALOG"),
                                    Map.of("type", "postback", "title", "🛒 មើលកន្ត្រក", "payload", "CART_VIEW"),
                                    Map.of("type", "postback", "title", "💳 គិតលុយ", "payload", "CART_CHECKOUT"),
                                    Map.of("type", "postback", "title", "📝 ប្រវត្តិបញ្ជាទិញ", "payload", "ORDER_HISTORY")
                            )
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

            log.info("Configured Messenger 'Get Started' button + persistent menu");
        } catch (Exception e) {
            // Non-fatal: page is still registered/usable, it just won't show the menu button until this succeeds.
            log.error("Failed to configure Messenger profile: {}", e.getMessage());
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
