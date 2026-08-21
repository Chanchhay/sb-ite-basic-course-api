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
            String itemsB64 = "";
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

            // Official ABA PayWay HMAC-SHA512 concatenation order
            String toHash = reqTime
                    + props.getMerchantId()
                    + cleanTranId
                    + amountStr
                    + itemsB64
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
            form.add("items", itemsB64);
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

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    props.getBaseUrl() + "/api/payment-gateway/v1/payments/purchase",
                    new HttpEntity<>(form, headers),
                    Map.class
            );

            log.info("ABA PayWay API raw response for tranId={}: {}", cleanTranId, response);

            if (response != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> statusMap = (Map<String, Object>) response.get("status");
                if (statusMap != null) {
                    log.info("ABA PayWay response status: code={}, message={}", statusMap.get("code"), statusMap.get("message"));
                }

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
            }
            log.warn("ABA PayWay response missing deeplink key: {}", response);
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