package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.bakong")
@Getter
@Setter
public class BakongProps {

    @Value("${app.bakong.base-url}")
    private String baseUrl;

    private Duration timeout = Duration.ofSeconds(8);
}
