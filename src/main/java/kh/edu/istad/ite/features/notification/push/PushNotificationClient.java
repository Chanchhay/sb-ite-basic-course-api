package kh.edu.istad.ite.features.notification.push;

import kh.edu.istad.ite.config.props.PushProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wakes a business owner's phone for an event this backend is the only thing
 * that knows happened — a channel order landing, with no browser or POS
 * session in the loop to trigger the dashboard's own client-side push call.
 * The dashboard app owns Web Push subscriptions and VAPID keys; this just
 * tells it who to notify and with what, over the one webhook it exposes for
 * exactly this. Best-effort like the Telegram alerts sent from the same
 * checkout paths: a slow or unreachable dashboard must never hold up or fail
 * a checkout, so every failure is swallowed here and only logged.
 */
@Component
@Slf4j
public class PushNotificationClient {

    private final RestClient restClient;
    private final PushProps props;

    public PushNotificationClient(PushProps props) {
        this.props = props;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public void notifyOwner(UUID keycloakUserId, String title, String body, String url, String tag) {
        if (keycloakUserId == null || !StringUtils.hasText(props.getDashboardBaseUrl())
                || !StringUtils.hasText(props.getInternalSecret())) {
            return;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userIds", List.of(keycloakUserId.toString()));
            requestBody.put("title", title);
            requestBody.put("body", body);
            if (url != null) {
                requestBody.put("url", url);
            }
            if (tag != null) {
                requestBody.put("tag", tag);
            }

            restClient.post()
                    .uri(props.getDashboardBaseUrl() + "/api/push/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Push-Secret", props.getInternalSecret())
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            log.warn("Push notification to owner {} failed: {} -> {}",
                    keycloakUserId, exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            log.warn("Push notification to owner {} failed: {}", keycloakUserId, exception.getMessage());
        }
    }
}
