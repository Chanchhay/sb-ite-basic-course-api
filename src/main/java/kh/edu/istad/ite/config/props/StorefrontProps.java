package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "app.storefront")
@Getter
@Setter
public class StorefrontProps {

    private String baseDomain = "fluxibiz.store";

    private String protocol = "https";

    private String pathPrefix = "/store";

    private boolean subdomainEnabled = false;
    private Set<String> reservedSlugs = new LinkedHashSet<>(Set.of(
            "www", "api", "admin", "auth", "app", "mail", "smtp", "ftp",
            "static", "cdn", "assets", "img", "images", "media",
            "dashboard", "store", "stores", "shop", "public", "account",
            "billing", "pay", "payment", "payments", "checkout", "invoice",
            "support", "help", "docs", "blog", "status", "about", "contact",
            "dev", "staging", "test", "demo", "sandbox", "local", "localhost",
            "keycloak", "s3", "minio", "db", "database", "ns1", "ns2", "mx",
            "my", "me", "user", "users", "customer", "customers", "business",
            "fluxibiz", "pos", "portal", "partner", "partners", "merchant"
    ));
}
