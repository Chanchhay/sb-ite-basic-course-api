package kh.edu.istad.ite.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
        @Bean
        public SecurityFilterChain configureApiSecurity(HttpSecurity http,
                                                        JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter)));

                http.cors(Customizer.withDefaults());
                http.csrf(AbstractHttpConfigurer::disable);
                http.formLogin(AbstractHttpConfigurer::disable);
                http.httpBasic(Customizer.withDefaults());
                http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                http.authorizeHttpRequests(endpoints -> endpoints
                        // Public endpoints
                        // The container's HEALTHCHECK probes this unauthenticated;
                        // only /health is exposed, so nothing else is reachable.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefronts/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefronts/*/items").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/scalar/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/register/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/telegram/**").permitAll()
                        .requestMatchers("/api/v1/social/facebook/webhook", "/api/v1/social/facebook/webhook/**", "/api/webhook", "/api/webhook/**", "/api/v1/social/facebook/webhook/setup").permitAll()                        .requestMatchers(HttpMethod.GET, "/api/v1/social/facebook/oauth/callback").permitAll()
                        .requestMatchers(
                                "/ws/customer-display",
                                "/ws/customer-display/**",
                                "/ws/customer-display-sockjs",
                                "/ws/customer-display-sockjs/**",
                                "/ws/notifications",
                                "/ws/notifications/**",
                                "/ws/notifications-sockjs",
                                "/ws/notifications-sockjs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/business-categories",
                                "/api/v1/business-categories/**")
                        .permitAll()

                        // order
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/orders/*/pay")
                        .hasAuthority("SCOPE_order:pay")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/orders/*/cancel")
                        .hasAuthority("SCOPE_order:cancel")

                        // Admin Dashboard
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/dashboard")
                        .hasAuthority("SCOPE_admin-dashboard:read")

                        // Admin Businesses
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/businesses",
                                "/api/v1/admin/businesses/**")
                        .hasAuthority("SCOPE_admin-business:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/businesses/**")
                        .hasAuthority("SCOPE_admin-business:delete")
                        .requestMatchers("/api/v1/admin/businesses", "/api/v1/admin/businesses/**")
                        .hasAuthority("SCOPE_admin-business:manage")

                        // Admin Business Categories
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/business-categories",
                                "/api/v1/admin/business-categories/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/business-categories")
                        .hasAuthority("SCOPE_admin-category:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/business-categories/**")
                        .hasAuthority("SCOPE_admin-category:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/business-categories/**")
                        .hasAuthority("SCOPE_admin-category:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/business-categories/**")
                        .hasAuthority("SCOPE_admin-category:delete")

                        // Admin Units
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/units", "/api/v1/admin/units/**")
                        .hasAuthority("SCOPE_admin-unit:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/units")
                        .hasAuthority("SCOPE_admin-unit:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/units/**")
                        .hasAuthority("SCOPE_admin-unit:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/units/**")
                        .hasAuthority("SCOPE_admin-unit:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/units/**")
                        .hasAuthority("SCOPE_admin-unit:delete")

                        // Admin Audit Logs
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit-logs",
                                "/api/v1/admin/audit-logs/**")
                        .hasAuthority("SCOPE_admin-audit:read")

                        // User Profile
                        .requestMatchers(HttpMethod.GET, "/api/v1/user-profiles/me")
                        .hasAuthority("SCOPE_profile:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/user-profiles/me")
                        .hasAuthority("SCOPE_profile:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/user-profiles/me")
                        .hasAuthority("SCOPE_profile:update")
                        .requestMatchers(HttpMethod.POST, "/api/v1/user-profiles/me/picture")
                        .hasAuthority("SCOPE_profile:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/user-profiles/me/picture")
                        .hasAuthority("SCOPE_profile:update")

                        // Business
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/me", "/api/v1/businesses")
                        .hasAuthority("SCOPE_business:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*")
                        .hasAuthority("SCOPE_business:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses")
                        .hasAuthority("SCOPE_business:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*")
                        .hasAuthority("SCOPE_business:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*")
                        .hasAuthority("SCOPE_business:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*")
                        .hasAuthority("SCOPE_business:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/delete")
                        .hasAuthority("SCOPE_business:delete")

                        // Items
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/items",
                                "/api/v1/businesses/*/items/*")
                        .hasAuthority("SCOPE_item:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/items")
                        .hasAuthority("SCOPE_item:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/items/*")
                        .hasAuthority("SCOPE_item:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/items/*")
                        .hasAuthority("SCOPE_item:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*/items/*")
                        .hasAuthority("SCOPE_item:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/items/*/images")
                        .hasAuthority("SCOPE_item:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*/items/*/images/*")
                        .hasAuthority("SCOPE_item:update")

                        // Item Groups
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/item-groups",
                                "/api/v1/businesses/*/item-groups/*")
                        .hasAuthority("SCOPE_item-group:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/item-groups")
                        .hasAuthority("SCOPE_item-group:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/item-groups/*")
                        .hasAuthority("SCOPE_item-group:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/item-groups/*")
                        .hasAuthority("SCOPE_item-group:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*/item-groups/*")
                        .hasAuthority("SCOPE_item-group:delete")

                        // Stock
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/stock-entries",
                                "/api/v1/businesses/*/stock-entries/*")
                        .hasAuthority("SCOPE_stock:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/items/*/stock-entries",
                                "/api/v1/businesses/*/items/*/stock")
                        .hasAuthority("SCOPE_stock:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/stock-entries",
                                "/api/v1/businesses/*/items/*/stock-entries")
                        .hasAuthority("SCOPE_stock:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/stock-entries/*",
                                "/api/v1/businesses/*/items/*/stock-entries/*")
                        .hasAuthority("SCOPE_stock:write")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/stock-entries/*",
                                "/api/v1/businesses/*/items/*/stock-entries/*")
                        .hasAuthority("SCOPE_stock:write")

                        // Orders
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/orders",
                                "/api/v1/businesses/*/orders/*")
                        .hasAuthority("SCOPE_order:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/orders/*/payment-status")
                        .hasAuthority("SCOPE_order:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/orders")
                        .hasAuthority("SCOPE_order:create")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/orders/*/pay")
                        .hasAuthority("SCOPE_order:pay")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/orders/*/cancel")
                        .hasAuthority("SCOPE_order:cancel")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/orders/*/khqr")
                        .hasAuthority("SCOPE_order:generate-khqr")

                        // Currencies
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/currencies",
                                "/api/v1/businesses/*/currencies/*")
                        .hasAuthority("SCOPE_currency:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/currencies")
                        .hasAuthority("SCOPE_currency:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/currencies/*")
                        .hasAuthority("SCOPE_currency:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/currencies/*")
                        .hasAuthority("SCOPE_currency:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*/currencies/*")
                        .hasAuthority("SCOPE_currency:delete")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/currencies/*/base")
                        .hasAuthority("SCOPE_currency:set-base")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/currencies/*/base")
                        .hasAuthority("SCOPE_currency:set-base")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/currencies/*/display")
                        .hasAuthority("SCOPE_currency:set-display")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/currencies/*/display")
                        .hasAuthority("SCOPE_currency:set-display")

                        // Storefront
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/storefront",
                                "/api/v1/businesses/storefront/*")
                        .hasAuthority("SCOPE_storefront:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/storefront",
                                "/api/v1/businesses/storefront/*")
                        .hasAuthority("SCOPE_storefront:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/storefront",
                                "/api/v1/businesses/storefront/*")
                        .hasAuthority("SCOPE_storefront:update")

                        // Telegram Settings
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/social-settings/telegram-bot",
                                "/api/v1/businesses/social-settings/telegram-bot/*")
                        .hasAuthority("SCOPE_telegram-setting:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/social-settings/telegram-bot",
                                "/api/v1/businesses/social-settings/telegram-bot/*")
                        .hasAuthority("SCOPE_telegram-setting:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/social-settings/telegram-bot",
                                "/api/v1/businesses/social-settings/telegram-bot/*")
                        .hasAuthority("SCOPE_telegram-setting:update")

                        // Bakong Settings
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/payment-settings/bakong",
                                "/api/v1/businesses/payment-settings/bakong/*")
                        .hasAuthority("SCOPE_bakong-setting:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/payment-settings/bakong/preview")
                        .hasAuthority("SCOPE_bakong-setting:preview")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/payment-settings/bakong",
                                "/api/v1/businesses/payment-settings/bakong/*")
                        .hasAuthority("SCOPE_bakong-setting:update")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/payment-settings/bakong",
                                "/api/v1/businesses/payment-settings/bakong/*")
                        .hasAuthority("SCOPE_bakong-setting:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/payment-settings/bakong",
                                "/api/v1/businesses/payment-settings/bakong/*")
                        .hasAuthority("SCOPE_bakong-setting:update")

                        // Shared Units
                        .requestMatchers(HttpMethod.GET, "/api/v1/units", "/api/v1/units/**")
                        .hasAuthority("SCOPE_unit:read")

                        // Business Role Management
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/roles",
                                "/api/v1/businesses/*/roles/*")
                        .hasAuthority("SCOPE_role:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/roles")
                        .hasAuthority("SCOPE_role:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/roles/*")
                        .hasAuthority("SCOPE_role:update")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/*/roles/*")
                        .hasAuthority("SCOPE_role:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*/roles/*")
                        .hasAuthority("SCOPE_role:delete")

                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/*/members",
                                "/api/v1/businesses/*/members/*")
                        .hasAuthority("SCOPE_member:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/*/members/*/roles")
                        .hasAuthority("SCOPE_role:assign")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/*/members/*/roles")
                        .hasAuthority("SCOPE_role:assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/*/members/*/roles")
                        .hasAuthority("SCOPE_role:assign")
                        .requestMatchers("/api/v1/businesses/*/members", "/api/v1/businesses/*/members/**")
                        .hasAuthority("SCOPE_member:manage")

                        .anyRequest().authenticated());

                return http.build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {

                Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {

                        Collection<GrantedAuthority> authorities = new ArrayList<>();

                        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

                        if (realmAccess != null
                                && realmAccess.get("roles") instanceof Collection<?> roles) {

                                roles.stream()
                                        .filter(String.class::isInstance)
                                        .map(String.class::cast)
                                        .map(role -> new SimpleGrantedAuthority(
                                                "ROLE_" + role))
                                        .forEach(authorities::add);
                        }

                        // Endpoint permissions
                        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

                        if (resourceAccess != null
                                && resourceAccess.get("fluxipos-backend") instanceof Map<?, ?> backendAccess
                                && backendAccess.get("roles") instanceof Collection<?> permissions) {

                                permissions.stream()
                                        .filter(String.class::isInstance)
                                        .map(String.class::cast)
                                        .map(permission -> new SimpleGrantedAuthority(
                                                "SCOPE_" + permission))
                                        .forEach(authorities::add);
                        }

                        return authorities;
                };

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(
                        authoritiesConverter);

                return converter;
        }

}
