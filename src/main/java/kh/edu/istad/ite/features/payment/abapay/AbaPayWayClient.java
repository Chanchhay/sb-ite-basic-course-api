package kh.edu.istad.ite.features.payment.abapay;

import kh.edu.istad.ite.config.props.AbaPayWayProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class AbaPayWayClient {

    private static final DateTimeFormatter REQ_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AbaPayWayProps props;
    private final RestTemplate restTemplate = new RestTemplate();

    public Optional<String> createAbapayDeeplink(String tranId, BigDecimal amount, String currency, String returnUrl) {
        if (!props.isEnabled() || props.getMerchantId() == null || props.getApiKey() == null) {
            return Optional.empty();
        }

        try {
            String cleanTranId = tranId != null ? tranId.replace("-", "") : "";
            if (cleanTranId.length() > 20) {
                cleanTranId = cleanTranId.substring(0, 20);
            }

            String reqTime = LocalDateTime.now().format(REQ_TIME);
            String amountStr = amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();
            String b64ReturnUrl = Base64.getEncoder().encodeToString(returnUrl.getBytes(StandardCharsets.UTF_8));

            // Hash = HMAC-SHA512(base64) over concatenated fields, per ABA's docs.
            String toHash = reqTime + props.getMerchantId() + cleanTranId + amountStr
                    + "" // items (empty)
                    + "" // shipping (empty)
                    + b64ReturnUrl
                    + "" // continue_success_url
                    + "" // return_deeplink
                    + currency
                    + "" // custom_fields
                    + "abapay_deeplink"; // payment_option
            String hash = hmacSha512Base64(toHash, props.getApiKey());

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("req_time", reqTime);
            form.add("merchant_id", props.getMerchantId());
            form.add("tran_id", cleanTranId);
            form.add("amount", amountStr);
            form.add("currency", currency);
            form.add("payment_option", "abapay_deeplink");
            form.add("return_url", b64ReturnUrl);
            form.add("hash", hash);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    props.getBaseUrl() + "/api/payment-gateway/v1/payments/purchase",
                    new HttpEntity<>(form, headers),
                    Map.class
            );

            if (response != null && response.get("abapay_deeplink") != null) {
                return Optional.of(response.get("abapay_deeplink").toString());
            }
            log.warn("ABA PayWay response missing abapay_deeplink: {}", response);
            return Optional.empty();
        } catch (Exception e) {
            log.error("ABA PayWay createAbapayDeeplink failed for tranId={}", tranId, e);
            return Optional.empty();
        }
    }

    private static String hmacSha512Base64(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}