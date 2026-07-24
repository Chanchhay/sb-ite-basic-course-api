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
}
