package kh.edu.istad.ite.shared.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Verifies the {@code signed_request} Facebook appends to a Messenger
 * webview URL (when opened via a {@code web_url} button with
 * {@code messenger_extensions: true}). Same role as
 * {@link TelegramInitDataValidator}: this is the only proof the webview
 * request actually came from Messenger and from the PSID it claims — without
 * it, anyone could POST a fabricated psid and be treated as that customer.
 *
 * Format: {@code "{signature}.{payload}"}, both base64url (no padding), where
 * {@code signature = HMAC-SHA256(payload, app_secret)}.
 * See https://developers.facebook.com/docs/messenger-platform/webview/extensions#signed_request
 */
public final class FacebookSignedRequestValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FacebookSignedRequestValidator() {
    }

    /**
     * @param signedRequest the raw {@code signed_request} query parameter
     * @param appSecret     this Facebook app's App Secret — the key the
     *                      signature is computed with
     * @return the decoded payload (carries {@code psid}, {@code thread_type}, etc.)
     * @throws IllegalArgumentException if malformed or the signature doesn't match
     */
    public static JsonNode verifyAndParse(String signedRequest, String appSecret) {
        if (signedRequest == null || signedRequest.isBlank()) {
            throw new IllegalArgumentException("signed_request is empty");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("Facebook app secret is not configured");
        }

        String[] parts = signedRequest.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("signed_request is not in {signature}.{payload} form");
        }

        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] receivedSignature;
        byte[] payloadBytes;
        try {
            receivedSignature = decoder.decode(pad(parts[0]));
            payloadBytes = decoder.decode(pad(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("signed_request is not valid base64url", e);
        }

        byte[] computedSignature = hmacSha256(appSecret.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8));

        if (!constantTimeEquals(receivedSignature, computedSignature)) {
            throw new IllegalArgumentException("signed_request signature does not match");
        }

        try {
            return MAPPER.readTree(payloadBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("signed_request payload is not valid JSON", e);
        }
    }

    /** base64url as sent by Facebook drops padding; Java's decoder wants it back. */
    private static String pad(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "=".repeat(4 - remainder);
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
