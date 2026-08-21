package kh.edu.istad.ite.features.payment.abapay;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.istad.ite.config.props.AbaPayWayProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<String> createAbapayDeeplink(String tranId, BigDecimal amount, String currency, String returnUrl) {
        if (!props.isEnabled()) {
            log.warn("ABA PayWay is disabled (app.aba-payway.enabled=false)");
            return Optional.empty();
        }
        if (!StringUtils.hasText(props.getMerchantId()) || !StringUtils.hasText(props.getApiKey())) {
            log.warn("ABA PayWay missing merchantId or apiKey (merchantId={}, apiKeyPresent={})",
                    props.getMerchantId(), StringUtils.hasText(props.getApiKey()));
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

            // Every field below (present or blank) participates in the hash, and the
            // ORDER is fixed by ABA's docs — it is NOT the same as the order fields
            // appear in the request form. Getting this order wrong is indistinguishable
            // from a network failure: PayWay just silently rejects with "Wrong Hash"
            // and an empty-ish response, which is exactly what we were seeing.
            String items = "";
            String shipping = "";
            String firstname = "";
            String lastname = "";
            String email = "";
            String phone = "";
            String type = "purchase";
            String paymentOption = "abapay_deeplink";
            String cancelUrl = "";
            String continueSuccessUrl = "";
            String returnDeeplink = "";
            String customFields = "";
            String returnParams = "";

            String toHash = reqTime + props.getMerchantId() + cleanTranId + amountStr
                    + items
                    + shipping
                    + firstname
                    + lastname
                    + email
                    + phone
                    + type
                    + paymentOption
                    + b64ReturnUrl
                    + cancelUrl
                    + continueSuccessUrl
                    + returnDeeplink
                    + currency
                    + customFields
                    + returnParams;
            String hash = hmacSha512Base64(toHash, props.getApiKey());

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("req_time", reqTime);
            form.add("merchant_id", props.getMerchantId());
            form.add("tran_id", cleanTranId);
            form.add("amount", amountStr);
            form.add("items", items);
            form.add("shipping", shipping);
            form.add("firstname", firstname);
            form.add("lastname", lastname);
            form.add("email", email);
            form.add("phone", phone);
            form.add("type", type);
            form.add("payment_option", paymentOption);
            form.add("return_url", b64ReturnUrl);
            form.add("cancel_url", cancelUrl);
            form.add("continue_success_url", continueSuccessUrl);
            form.add("return_deeplink", returnDeeplink);
            form.add("currency", currency);
            form.add("custom_fields", customFields);
            form.add("return_params", returnParams);
            form.add("hash", hash);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Use exchange() + String (not postForObject + Map) so a non-2xx status or an
            // unparsable body is fully visible in the log instead of collapsing to "null" —
            // that blind spot is exactly what made the previous bug hard to diagnose.
            ResponseEntity<String> httpResponse = restTemplate.exchange(
                    props.getBaseUrl() + "/api/payment-gateway/v1/payments/purchase",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    String.class
            );

            log.info("ABA PayWay HTTP {} for tranId={}: {}",
                    httpResponse.getStatusCode(), cleanTranId, httpResponse.getBody());

            if (!httpResponse.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(httpResponse.getBody())) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(httpResponse.getBody(), Map.class);

            if (response.get("abapay_deeplink") != null) {
                return Optional.of(response.get("abapay_deeplink").toString());
            }
            if (response.get("deeplink") != null) {
                return Optional.of(response.get("deeplink").toString());
            }
            if (response.get("app_checkout_url") != null) {
                return Optional.of(response.get("app_checkout_url").toString());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data != null && data.get("abapay_deeplink") != null) {
                return Optional.of(data.get("abapay_deeplink").toString());
            }

            log.warn("ABA PayWay response missing deeplink key for tranId={}: {}", cleanTranId, response);
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