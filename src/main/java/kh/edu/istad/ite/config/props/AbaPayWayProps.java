package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.aba-payway")
@Getter
@Setter
public class AbaPayWayProps {
    private String baseUrl = "https://checkout-sandbox.payway.com.kh";
    private String merchantId;
    private String apiKey;
    private boolean enabled = false;
}