package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.telegram")
@Getter
@Setter
public class TelegramProps {

    @Value("${app.telegram.api-base-url}")
    private String apiBaseUrl = "https://api.telegram.org";

    @Value("${app.telegram.webhook-base-url}")
    private String webhookBaseUrl;
}
