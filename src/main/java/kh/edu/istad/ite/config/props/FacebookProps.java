package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.facebook")
@Getter
@Setter
public class FacebookProps {

    @Value("${app.facebook.graph-base-url:https://graph.facebook.com}")
    private String graphBaseUrl = "https://graph.facebook.com";

    @Value("${app.facebook.api-version:v19.0}")
    private String apiVersion = "v19.0";

    @Value("${app.facebook.webhook-verify-token:fluxibiz_verify_token}")
    private String webhookVerifyToken = "fluxibiz_verify_token";

    @Value("${app.facebook.app-id:}")
    private String appId;

    @Value("${app.facebook.app-secret:}")
    private String appSecret;

    @Value("${app.facebook.redirect-uri:}")
    private String redirectUri;

    @Value("${app.facebook.frontend-result-url:}")
    private String frontendResultUrl;
}

