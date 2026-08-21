package kh.edu.istad.ite.features.payment.bakong;

import kh.edu.istad.ite.config.props.BakongProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class BakongTransactionClient {

    private static final String CHECK_PATH = "/v1/check_transaction_by_md5";
    private static final String DEEPLINK_PATH = "/v1/generate_deeplink_by_qr";
    private static final int RESPONSE_CODE_SUCCESS = 0;

    private final RestClient restClient;

    public BakongTransactionClient(BakongProps props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    public BakongCheckResult checkByMd5(String accessToken, String md5) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(CHECK_PATH)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("md5", md5))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            return interpret(body);

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            // The shop's Bakong API token has expired or been revoked. Shouting
            // about this is the whole point: silently returning "not paid" makes
            // a real payment look pending forever.
            log.error("Bakong rejected the API token while checking md5 {} — the shop must renew it. {}",
                    md5, exception.getMessage());
            return BakongCheckResult.unverifiable(
                    "Bakong rejected this shop's API token. The shop needs to renew it.");

        } catch (RestClientException exception) {
            log.error("Bakong check failed for md5 {}: {}", md5, exception.getMessage());
            return BakongCheckResult.unverifiable("Could not reach Bakong: " + exception.getMessage());
        }
    }

    public Optional<String> generateDeeplinkByQr(String accessToken, String qr, String callbackUrl, String appName) {
        try {
            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("appIconUrl", "");
            sourceInfo.put("appName", appName);
            sourceInfo.put("appDeepLinkCallback", callbackUrl != null ? callbackUrl : "");

            Map<String, Object> body = restClient.post()
                    .uri(DEEPLINK_PATH)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("qr", qr, "sourceInfo", sourceInfo))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (body == null) {
                return Optional.empty();
            }

            Number responseCode = (Number) body.get("responseCode");
            if (responseCode == null || responseCode.intValue() != RESPONSE_CODE_SUCCESS) {
                log.warn("Bakong generate_deeplink_by_qr rejected: {}", body.get("responseMessage"));
                return Optional.empty();
            }

            Object data = body.get("data");
            if (data instanceof Map<?, ?> map && map.get("shortLink") != null) {
                return Optional.of(String.valueOf(map.get("shortLink")));
            }
            return Optional.empty();

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            log.error("Bakong rejected the API token while generating a deeplink — the shop must renew it. {}",
                    exception.getMessage());
            return Optional.empty();

        } catch (RestClientException exception) {
            log.error("Bakong generate_deeplink_by_qr failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private BakongCheckResult interpret(Map<String, Object> body) {
        if (body == null) {
            return BakongCheckResult.unverifiable("Empty response from Bakong");
        }

        Number responseCode = (Number) body.get("responseCode");
        String message = String.valueOf(body.getOrDefault("responseMessage", ""));

        // responseCode 0 = paid, 1 = transaction not found (i.e. not paid yet).
        if (responseCode == null || responseCode.intValue() != RESPONSE_CODE_SUCCESS) {
            log.debug("Bakong says not paid: code={} message={}", responseCode, message);
            return BakongCheckResult.notPaid(message);
        }

        Object data = body.get("data");
        if (!(data instanceof Map<?, ?> transaction)) {
            return BakongCheckResult.unverifiable("Success without transaction details");
        }

        return new BakongCheckResult(
                true,
                false,
                message,
                asString(transaction.get("hash")),
                asString(transaction.get("fromAccountId")),
                asAmount(transaction.get("amount")),
                asString(transaction.get("currency")),
                transaction.get("acknowledgedDateMs") instanceof Number ms ? ms.longValue() : null
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal asAmount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}