package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.public-api")
@Getter
@Setter
public class PublicApiProps {

    private String baseUrl = "http://localhost:8080";
}