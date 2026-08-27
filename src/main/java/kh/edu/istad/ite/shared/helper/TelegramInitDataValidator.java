package kh.edu.istad.ite.shared.helper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verifies a Telegram Mini App's {@code initData} string per Telegram's
 * documented algorithm (https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app).
 * This is the only proof a Mini App request actually came from Telegram and
 * from the specific user it claims — skipping it would let anyone POST a
 * fabricated {@code user} object and be treated as that customer.
 */
public final class TelegramInitDataValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    /** Fixed per Telegram's spec — not a project secret, just this algorithm's constant. */
    private static final String WEB_APP_DATA_KEY = "WebAppData";

    private TelegramInitDataValidator() {
    }

    /**
     * @param initData    the raw query-string the Telegram client attaches as
     *                    {@code Telegram.WebApp.initData}
     * @param botToken    this business's own bot token (plaintext, already
     *                    decrypted) — the secret the signature is keyed on
     * @param maxAgeSeconds rejects a technically-valid but stale payload (a
     *                    captured/replayed initData), independent of the
     *                    signature check
     * @return the parsed fields (including {@code user}, still raw JSON) if
     *         the signature and freshness both check out
     * @throws IllegalArgumentException if the signature is missing, wrong, or the data is too old
     */
    public static Map<String, String> verifyAndParse(String initData, String botToken, long maxAgeSeconds) {
        if (initData == null || initData.isBlank()) {
            throw new IllegalArgumentException("initData is empty");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("bot token is not configured");
        }

        Map<String, String> fields = parseQueryString(initData);
        String receivedHash = fields.remove("hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new IllegalArgumentException("initData carries no hash");
        }

        // Alphabetically-sorted "key=value" lines, joined by \n — the exact
        // string the signature was computed over.
        String dataCheckString = new TreeMap<>(fields).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        byte[] secretKey = hmacSha256(WEB_APP_DATA_KEY.getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        byte[] computedHash = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        String computedHashHex = HexFormat.of().formatHex(computedHash);

        if (!constantTimeEquals(computedHashHex, receivedHash.toLowerCase())) {
            throw new IllegalArgumentException("initData signature does not match");
        }

        String authDateRaw = fields.get("auth_date");
        if (authDateRaw != null) {
            long authDate;
            try {
                authDate = Long.parseLong(authDateRaw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("initData auth_date is not a number");
            }
            long age = Instant.now().getEpochSecond() - authDate;
            if (age > maxAgeSeconds) {
                throw new IllegalArgumentException("initData is stale (age " + age + "s, max " + maxAgeSeconds + "s)");
            }
        }

        return fields;
    }

    private static Map<String, String> parseQueryString(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) continue;
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
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

    /** Avoids leaking timing information about how much of the hash matched. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
