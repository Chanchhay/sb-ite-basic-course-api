package kh.edu.istad.ite.features.payment.bakong;

import kh.edu.istad.ite.config.props.BakongProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Slf4j
public class BakongTransactionClient {

    private static final String CHECK_PATH = "/v1/check_transaction_by_md5";
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
        } catch (RestClientException exception) {
            log.warn("Bakong check failed for md5 {}: {}", md5, exception.getMessage());
            return BakongCheckResult.notPaid("Could not reach Bakong: " + exception.getMessage());
        }
    }

    private BakongCheckResult interpret(Map<String, Object> body) {
        if (body == null) {
            return BakongCheckResult.notPaid("Empty response from Bakong");
        }

        Number responseCode = (Number) body.get("responseCode");
        String message = String.valueOf(body.getOrDefault("responseMessage", ""));

        if (responseCode == null || responseCode.intValue() != RESPONSE_CODE_SUCCESS) {
            return BakongCheckResult.notPaid(message);
        }

        Object data = body.get("data");
        if (!(data instanceof Map<?, ?> transaction)) {
            return BakongCheckResult.notPaid("Success without transaction details");
        }

        return new BakongCheckResult(
                true,
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
