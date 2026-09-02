package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.push")
@Getter
@Setter
public class PushProps {

    @Value("${app.push.dashboard-base-url:}")
    private String dashboardBaseUrl;

    @Value("${app.push.internal-secret:}")
    private String internalSecret;
}
