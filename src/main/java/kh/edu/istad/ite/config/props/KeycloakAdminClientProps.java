package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
@Getter
@Setter
public class KeycloakAdminClientProps {
    private String serverUrl;
    private String clientId;
    private String clientSecret;
    private String realm;
    private String targetRealm;
    private boolean sendVerificationEmail;

    /** Where the verification link sends business owners once confirmed. */
    private VerifyEmailTarget businessVerifyEmail = new VerifyEmailTarget();

    /** Where the verification link sends storefront customers once confirmed. */
    private VerifyEmailTarget customerVerifyEmail = new VerifyEmailTarget();

    /**
     * The client the verification link is issued for, and the URL Keycloak
     * redirects to afterwards. The URL must be listed in that client's
     * "Valid redirect URIs" or Keycloak refuses to send the email.
     */
    @Getter
    @Setter
    public static class VerifyEmailTarget {
        private String clientId;
        private String redirectUri;
    }
}
