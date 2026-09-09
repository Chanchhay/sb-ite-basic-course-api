package kh.edu.istad.ite.features.notification.push;

import kh.edu.istad.ite.config.props.PushProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
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
        if (keycloakUserId == null) {
            log.warn("Push notification skipped: business has no keycloakUserId");
            return;
        }
        if (!StringUtils.hasText(props.getDashboardBaseUrl()) || !StringUtils.hasText(props.getInternalSecret())) {
            log.warn("Push notification to owner {} skipped: app.push.dashboard-base-url / app.push.internal-secret not configured",
                    keycloakUserId);
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

            Map<String, Object> result = restClient.post()
                    .uri(props.getDashboardBaseUrl() + "/api/push/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Push-Secret", props.getInternalSecret())
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            // The webhook itself always answers 200 — "no device is
            // subscribed for this owner" is a legitimate outcome it reports
            // in the body (sent: 0), not an HTTP error, so the only way to
            // tell a real delivery from a silent no-op is to read it.
            log.info("Push notification to owner {} ({}): {}", keycloakUserId, title, result);
        } catch (HttpStatusCodeException exception) {
            log.warn("Push notification to owner {} failed: {} -> {}",
                    keycloakUserId, exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            log.warn("Push notification to owner {} failed: {}", keycloakUserId, exception.getMessage());
        }
    }
}
