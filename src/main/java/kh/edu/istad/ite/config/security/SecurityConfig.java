package kh.edu.istad.ite.config.security;

import kh.edu.istad.ite.shared.ratelimit.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import kh.edu.istad.ite.features.audit.web.SignInAuditFilter;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        /**
         * Boot registers every {@code Filter} bean with the servlet container of its
         * own accord, which would run the rate limiter ahead of the security chain
         * and so ahead of CORS. Only the placement configured above should apply, so
         * the automatic registration is switched off here.
         */
        @Bean
        public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
                        RateLimitFilter rateLimitFilter) {
                FilterRegistrationBean<RateLimitFilter> registration =
                                new FilterRegistrationBean<>(rateLimitFilter);
                registration.setEnabled(false);
                return registration;
        }

        /** As above: kept out of the plain servlet chain so it runs only here. */
        @Bean
        public FilterRegistrationBean<SignInAuditFilter> signInAuditFilterRegistration(
                        SignInAuditFilter signInAuditFilter) {
                FilterRegistrationBean<SignInAuditFilter> registration =
                                new FilterRegistrationBean<>(signInAuditFilter);
                registration.setEnabled(false);
                return registration;
        }

        @Bean
        public SecurityFilterChain configureApiSecurity(HttpSecurity http,
                                                        JwtAuthenticationConverter jwtAuthenticationConverter,
                                                        BusinessAccessAuthorizationManager tenant,
                                                        RateLimitFilter rateLimitFilter,
                                                        SignInAuditFilter signInAuditFilter) throws Exception {

                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter)));

                http.cors(Customizer.withDefaults());

                // After CORS so a browser can read the 429 rather than reporting an
                // opaque network failure, and before authentication so a flood is
                // turned away without a token check or a database round trip.
                http.addFilterAfter(rateLimitFilter, CorsFilter.class);

                // After the bearer token has been turned into an authentication:
                // the session id it records lives in the JWT, so there is nothing
                // to see before this point. Anonymous and public requests fall
                // through it untouched.
                http.addFilterAfter(signInAuditFilter, BearerTokenAuthenticationFilter.class);
                http.csrf(AbstractHttpConfigurer::disable);
                http.formLogin(AbstractHttpConfigurer::disable);
                http.httpBasic(Customizer.withDefaults());
                http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                http.authorizeHttpRequests(endpoints -> endpoints
                        // Public endpoints
                        // The container's HEALTHCHECK probes this unauthenticated;
                        // only /health is exposed, so nothing else is reachable.
                        .requestMatchers("/api/v1/telegram/webhook/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()

                        // Push subscription lookup/prune: no dashboard user in the
                        // loop, only the dashboard server itself asking who to wake
                        // for a push it was just told to send. Gated on its own
                        // shared secret inside PushSubscriptionController, the same
                        // one PushNotificationClient sends the other direction.
                        .requestMatchers("/api/v1/internal/push-subscriptions/**").permitAll()

                        // Cache hit rates and request timings. Operational detail
                        // about the running system, so it is read by the people who
                        // run it and by nobody else.
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/**")
                        .access(permissionOrSuperAdmin("admin-dashboard:read"))
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
                        // Verified by initData's own HMAC signature, not a bearer token — that's what this endpoint exists to issue.
                        .requestMatchers(HttpMethod.POST, "/api/v1/telegram-webapp/auth").permitAll()
                        // Same story for Messenger's signed_request, and for the device-registration
                        // fallback that replaced it — neither has a bearer token to check yet.
                        .requestMatchers(HttpMethod.POST, "/api/v1/facebook-webapp/auth", "/api/v1/facebook-webapp/device-auth").permitAll()
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
                        .access(permissionOrSuperAdmin("admin-dashboard:read"))

                        // Admin Businesses
                        // Assisted migration works on a customer's catalogue on
                        // their behalf, so it asks for the authority that manages
                        // a business rather than the one that merely reads one.
                        .requestMatchers("/api/v1/admin/businesses/*/assisted-migrations",
                                "/api/v1/admin/businesses/*/assisted-migrations/**")
                        .access(permissionOrSuperAdmin("admin-business:manage"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/businesses",
                                "/api/v1/admin/businesses/**")
                        .access(permissionOrSuperAdmin("admin-business:read"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/businesses/**")
                        .access(permissionOrSuperAdmin("admin-business:delete"))
                        .requestMatchers("/api/v1/admin/businesses", "/api/v1/admin/businesses/**")
                        .access(permissionOrSuperAdmin("admin-business:manage"))

                        // Admin Business Categories
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/business-categories",
                                "/api/v1/admin/business-categories/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/business-categories")
                        .access(permissionOrSuperAdmin("admin-category:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/business-categories/**")
                        .access(permissionOrSuperAdmin("admin-category:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/business-categories/**")
                        .access(permissionOrSuperAdmin("admin-category:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/business-categories/**")
                        .access(permissionOrSuperAdmin("admin-category:delete"))

                        // Admin Units
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/units", "/api/v1/admin/units/**")
                        .access(permissionOrSuperAdmin("admin-unit:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/units")
                        .access(permissionOrSuperAdmin("admin-unit:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/units/**")
                        .access(permissionOrSuperAdmin("admin-unit:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/units/**")
                        .access(permissionOrSuperAdmin("admin-unit:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/units/**")
                        .access(permissionOrSuperAdmin("admin-unit:delete"))

                        // A shop's own audit log: who signed in, and who changed
                        // staff or roles. Scoped to the caller's business by the
                        // controller, which is why there is no {businessId} here.
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-logs")
                        .access(permissionOrBusinessRole("audit:read"))

                        // Admin Audit Logs
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit-logs",
                                "/api/v1/admin/audit-logs/**")
                        .access(permissionOrSuperAdmin("admin-audit:read"))

                        // Admin Channels (read-only list of businesses' channel adoption)
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/channels")
                        .access(permissionOrSuperAdmin("admin-channel:read"))

                        // Sales Channels (the platform-wide channel catalog: POS, WEB, Telegram, ...)
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales-channels", "/api/v1/sales-channels/**")
                        .access(permissionOrBusinessRole("item:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales-channels")
                        .access(permissionOrSuperAdmin("admin-channel:manage"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sales-channels/**")
                        .access(permissionOrSuperAdmin("admin-channel:manage"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sales-channels/**")
                        .access(permissionOrSuperAdmin("admin-channel:manage"))

                        // Admin Platform Features
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/platform-features")
                        .access(permissionOrSuperAdmin("admin-platform-feature:read"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/platform-features/**")
                        .access(permissionOrSuperAdmin("admin-platform-feature:update"))

                        // Platform Roles
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/roles", "/api/v1/platform/roles/**")
                        .access(permissionOrSuperAdmin("role:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/platform/roles")
                        .access(permissionOrSuperAdmin("role:create"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/platform/roles/**")
                        .access(permissionOrSuperAdmin("role:update"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/roles/**")
                        .access(permissionOrSuperAdmin("role:update"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/roles/**")
                        .access(permissionOrSuperAdmin("role:delete"))

                        // Platform Staff. Creating/updating a staff member is how a role
                        // gets handed to them, so these reuse role:assign rather than
                        // introducing a staff-specific PermissionCode.
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/staff", "/api/v1/platform/staff/**")
                        .access(permissionOrSuperAdmin("role:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/platform/staff")
                        .access(permissionOrSuperAdmin("role:assign"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/platform/staff/**")
                        .access(permissionOrSuperAdmin("role:assign"))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/staff/**")
                        .access(permissionOrSuperAdmin("role:assign"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/staff/**")
                        .access(permissionOrSuperAdmin("role:assign"))

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
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/social-settings/facebook",
                                "/api/v1/businesses/social-settings/facebook/*")
                        .hasAuthority("SCOPE_business:update")
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
                        // "Which business am I in?" is identity, not data, and
                        // it must not need a permission. The dashboard resolves
                        // every other business id from this call and refuses to
                        // open at all when it fails, so gating it on
                        // business:read locked out every staff member whose role
                        // did not happen to include "View shop details" — a
                        // cashier with only order:* could not sign in.
                        //
                        // Nothing here can be forged or fished for: it answers
                        // for the caller's own business, found by ownership or
                        // by active staff membership, and returns the details of
                        // the shop they already work in.
                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses")
                        .hasAuthority("SCOPE_business:create")


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

                        .requestMatchers(HttpMethod.GET, "/api/v1/businesses/{businessId}/imports",
                                "/api/v1/businesses/{businessId}/imports/*",
                                "/api/v1/businesses/{businessId}/imports/*/columns",
                                "/api/v1/businesses/{businessId}/imports/*/preview",
                                "/api/v1/businesses/{businessId}/imports/*/rows",
                                "/api/v1/businesses/{businessId}/imports/*/errors",
                                "/api/v1/businesses/{businessId}/imports/*/report")
                        .access(scoped(tenant, "item:read"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/imports",
                                "/api/v1/businesses/{businessId}/imports/*/validate",
                                "/api/v1/businesses/{businessId}/imports/*/commit")
                        .access(scoped(tenant, "item:create"))
                        // Undoing an import deletes items, so it asks for the
                        // permission that deleting an item asks for.
                        .requestMatchers(HttpMethod.POST, "/api/v1/businesses/{businessId}/imports/*/revert")
                        .access(scoped(tenant, "item:delete"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/businesses/{businessId}/imports/*/mapping")
                        .access(scoped(tenant, "item:create"))

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
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/businesses/{businessId}/orders/*/pay-later/approve")
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

        private static AuthorizationManager<RequestAuthorizationContext> permissionOrSuperAdmin(String permission) {
                return AuthorizationManagers.anyOf(
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasAuthority("SCOPE_" + permission),
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasRole("SUPER_ADMIN"));
        }

        private static AuthorizationManager<RequestAuthorizationContext> permissionOrBusinessRole(String permission) {
                return AuthorizationManagers.anyOf(
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasAuthority("SCOPE_" + permission),
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasAuthority("SCOPE_admin-channel:read"),
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasRole("SUPER_ADMIN"),
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasRole("BUSINESS_OWNER"),
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasRole("BUSINESS"),
                                AuthorityAuthorizationManager
                                                .<RequestAuthorizationContext>hasRole("ADMIN"));
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
