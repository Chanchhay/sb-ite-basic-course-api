package kh.edu.istad.ite.config.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Slf4j
public class CredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @Value("${app.security.credential-encryption-key:}")
    private String encodedKey;

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(encodedKey)) {
            log.warn("app.security.credential-encryption-key is not set. "
                    + "Credential encryption is disabled and tokens cannot be stored.");
            return;
        }

        byte[] keyBytes = Base64.getDecoder().decode(encodedKey.trim());

        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.security.credential-encryption-key must decode to 32 bytes, got " + keyBytes.length);
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isEnabled() {
        return secretKey != null;
    }
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        requireEnabled();

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt credential", exception);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }

        requireEnabled();

        try {
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt credential", exception);
        }
    }

    private void requireEnabled() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "Credential encryption key is not configured. Set CREDENTIAL_ENCRYPTION_KEY.");
        }
    }
}
