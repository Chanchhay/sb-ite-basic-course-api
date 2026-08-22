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
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
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
                                                        JwtAuthenticationConverter jwtAuthenticationConverter,
                                                        BusinessAccessAuthorizationManager tenant) throws Exception {

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

                        // --- Business-wide settings, resolved from the caller ---
                        // These carry no {businessId}: the services behind them
                        // read the caller's own business. They must be matched
                        // before the Business block below, whose single-segment
                        // "/businesses/*" would otherwise swallow
                        // "/businesses/storefront" and demand business:read.

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
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/social-settings/telegram-bot")
                        .hasAuthority("SCOPE_telegram-setting:update")

                        // Facebook Settings. No PermissionCode of its own yet,
                        // so these borrow the business settings permissions —
                        // the same pairing the dashboard's Facebook Page screen
                        // uses. They resolve the business from the caller, so
                        // there is no id to forge; they are listed here only to
                        // keep the {businessId} catch-all from claiming them.
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/social-settings/facebook",
                                "/api/v1/businesses/social-settings/facebook/*")
                        .hasAuthority("SCOPE_business:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/social-settings/facebook")
                        .hasAuthority("SCOPE_business:update")

                        // Bakong Settings
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/payment-settings/bakong",
                                "/api/v1/businesses/payment-settings/bakong/*")
                        .hasAuthority("SCOPE_bakong-setting:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/payment-settings/bakong/preview-qr")
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

                        // --- The business collection ---
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/me", "/api/v1/businesses")
                        .hasAuthority("SCOPE_business:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses")
                        .hasAuthority("SCOPE_business:create")

                        // --- Everything below names a {businessId} ---
                        // `scoped` pairs the permission with membership of that
                        // business. The permission alone says the caller may read
                        // items; it does not say whose. Every matcher here must
                        // spell the variable {businessId}, not "*", or the
                        // membership check has nothing to read and denies.

                        // Business
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}")
                        .access(scoped(tenant, "business:read"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}")
                        .access(scoped(tenant, "business:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}")
                        .access(scoped(tenant, "business:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/logo",
                                "/api/v1/businesses/{businessId}/thumbnail")
                        .access(scoped(tenant, "business:update"))
                        // Both verbs: the controller maps PUT, and PUT
                        // "/businesses/{businessId}" above would otherwise claim
                        // this as a plain update.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/delete")
                        .access(scoped(tenant, "business:delete"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/delete")
                        .access(scoped(tenant, "business:delete"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}")
                        .access(scoped(tenant, "business:delete"))

                        // Facebook, addressed by id. Same pairing as the
                        // caller-resolved variants above; without a rule these
                        // would reach the catch-all, which checks membership but
                        // not what the member is allowed to do.
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/social/facebook",
                                "/api/v1/businesses/{businessId}/social/facebook/*")
                        .access(scoped(tenant, "business:read"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/social/facebook")
                        .access(scoped(tenant, "business:update"))

                        // Items
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/items",
                                "/api/v1/businesses/{businessId}/items/*")
                        .access(scoped(tenant, "item:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/items")
                        .access(scoped(tenant, "item:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/items/*")
                        .access(scoped(tenant, "item:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/items/*")
                        .access(scoped(tenant, "item:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/items/*")
                        .access(scoped(tenant, "item:delete"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/items/*/images")
                        .access(scoped(tenant, "item:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/items/*/images/*")
                        .access(scoped(tenant, "item:update"))

                        // Item Groups
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/item-groups",
                                "/api/v1/businesses/{businessId}/item-groups/*")
                        .access(scoped(tenant, "item-group:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/item-groups")
                        .access(scoped(tenant, "item-group:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/item-groups/*")
                        .access(scoped(tenant, "item-group:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/item-groups/*")
                        .access(scoped(tenant, "item-group:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/item-groups/*")
                        .access(scoped(tenant, "item-group:delete"))

                        // Stock
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/stock-entries",
                                "/api/v1/businesses/{businessId}/stock-entries/*")
                        .access(scoped(tenant, "stock:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/items/*/stock-entries",
                                "/api/v1/businesses/{businessId}/items/*/stock")
                        .access(scoped(tenant, "stock:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/stock-entries",
                                "/api/v1/businesses/{businessId}/items/*/stock-entries")
                        .access(scoped(tenant, "stock:write"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/stock-entries/*",
                                "/api/v1/businesses/{businessId}/items/*/stock-entries/*")
                        .access(scoped(tenant, "stock:write"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/stock-entries/*",
                                "/api/v1/businesses/{businessId}/items/*/stock-entries/*")
                        .access(scoped(tenant, "stock:write"))

                        // Orders
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/orders",
                                "/api/v1/businesses/{businessId}/orders/*")
                        .access(scoped(tenant, "order:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/orders/*/payment-status")
                        .access(scoped(tenant, "order:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/orders")
                        .access(scoped(tenant, "order:create"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/orders/*/pay")
                        .access(scoped(tenant, "order:pay"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/orders/*/pay")
                        .access(scoped(tenant, "order:pay"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/orders/*/cancel")
                        .access(scoped(tenant, "order:cancel"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/orders/*/cancel")
                        .access(scoped(tenant, "order:cancel"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/orders/*/khqr")
                        .access(scoped(tenant, "order:generate-khqr"))

                        // Currencies
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/currencies",
                                "/api/v1/businesses/{businessId}/currencies/*")
                        .access(scoped(tenant, "currency:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/currencies")
                        .access(scoped(tenant, "currency:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/currencies/*")
                        .access(scoped(tenant, "currency:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/currencies/*")
                        .access(scoped(tenant, "currency:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/currencies/*")
                        .access(scoped(tenant, "currency:delete"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/currencies/*/base")
                        .access(scoped(tenant, "currency:set-base"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/currencies/*/base")
                        .access(scoped(tenant, "currency:set-base"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/currencies/*/display")
                        .access(scoped(tenant, "currency:set-display"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/currencies/*/display")
                        .access(scoped(tenant, "currency:set-display"))

                        // Business Role Management
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/roles",
                                "/api/v1/businesses/{businessId}/roles/*")
                        .access(scoped(tenant, "role:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/roles")
                        .access(scoped(tenant, "role:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/roles/*")
                        .access(scoped(tenant, "role:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/roles/*")
                        .access(scoped(tenant, "role:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/roles/*")
                        .access(scoped(tenant, "role:delete"))

                        // Members and staff
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/members",
                                "/api/v1/businesses/{businessId}/members/*")
                        .access(scoped(tenant, "member:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/members/*/roles")
                        .access(scoped(tenant, "role:assign"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/members/*/roles")
                        .access(scoped(tenant, "role:assign"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/businesses/{businessId}/members/*/roles")
                        .access(scoped(tenant, "role:assign"))
                        .requestMatchers("/api/v1/businesses/{businessId}/members",
                                "/api/v1/businesses/{businessId}/members/**")
                        .access(scoped(tenant, "member:manage"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/staff",
                                "/api/v1/businesses/{businessId}/staff/*")
                        .access(scoped(tenant, "member:read"))
                        .requestMatchers("/api/v1/businesses/{businessId}/staff",
                                "/api/v1/businesses/{businessId}/staff/**")
                        .access(scoped(tenant, "member:manage"))

                        // Anything else under a business. Discounts, coupons,
                        // customers, membership types, add-ons, assets, sales
                        // reports and channel pricing have no permission of
                        // their own yet, so they were reaching
                        // `anyRequest().authenticated()` — which any signed-in
                        // stranger satisfies. Until each grows a PermissionCode
                        // this at least confines them to the business's own
                        // people. Keep it last: it matches everything the rules
                        // above did not.
                        .requestMatchers("/api/v1/businesses/{businessId}/**")
                        .access(tenant)

                        .anyRequest().authenticated());

                return http.build();
        }

        /**
         * The permission, and membership of the business in the path. Both must
         * hold: `item:read` says the caller may read items, `tenant` says whose.
         */
        private static AuthorizationManager<RequestAuthorizationContext> scoped(
                        BusinessAccessAuthorizationManager tenant, String permission) {

                return AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasAuthority("SCOPE_" + permission),
                                tenant);
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
