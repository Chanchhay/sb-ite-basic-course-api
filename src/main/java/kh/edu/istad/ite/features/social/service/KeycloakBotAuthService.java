package kh.edu.istad.ite.features.social.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakBotAuthService {

    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.admin.target-realm}")
    private String realm;

    @Value("${keycloak.admin.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String clientId;

    @Value("${keycloak.admin.client-secret:}")
    private String clientSecret;


    public record KeycloakUserInfo(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            String phoneNumber
    ) {
        public String getFullName() {
            String first = firstName != null ? firstName : "";
            String last = lastName != null ? lastName : "";
            String full = (first + " " + last).trim();
            return full.isEmpty() ? username : full;
        }
    }


    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    /** Sets/resets a Keycloak user's password without needing the old one — used to mint a real login for a Telegram-verified identity that never had a password of its own. */
    public boolean setPassword(String userId, String password) {
        try {
            RealmResource realmResource = keycloakAdminClient.realm(realm);
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            realmResource.users().get(userId).resetPassword(credential);
            return true;
        } catch (Exception e) {
            log.error("Failed to set password for Keycloak user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Direct Access Grant with a password this service itself just set —
     * the only way to hand a caller a real, usable access token for a user
     * who authenticated via Telegram rather than a Keycloak login form. The
     * password is generated fresh and reset on every call, so nothing about
     * it needs to be remembered afterward.
     */
    public TokenResponse passwordGrantTokens(String username, String password) {
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .build();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                formData.add("client_secret", clientSecret);
            }
            formData.add("username", username);
            formData.add("password", password);

            Map<String, Object> tokenResponse = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                return null;
            }

            Object expiresIn = tokenResponse.get("expires_in");
            return new TokenResponse(
                    String.valueOf(tokenResponse.get("access_token")),
                    String.valueOf(tokenResponse.get("refresh_token")),
                    expiresIn instanceof Number number ? number.longValue() : 0L
            );
        } catch (Exception e) {
            log.warn("Password-grant token fetch failed for {}: {}", username, e.getMessage());
            return null;
        }
    }

    public boolean registerInKeycloak(String username, String password, String phoneNumber) {
        try {
            RealmResource realmResource = keycloakAdminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> existingUsers = usersResource.search(username, true);
            if (!existingUsers.isEmpty()) {
                log.warn("Keycloak user registration failed: Username {} already exists", username);
                return false;
            }

            UserRepresentation user = new UserRepresentation();
            user.setEnabled(true);
            user.setUsername(username);
            user.singleAttribute("phoneNumber", phoneNumber);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            Response response = usersResource.create(user);
            if (response.getStatus() == 201) {
                log.info("Successfully registered user {} into Keycloak", username);
                return true;
            } else {
                log.error("Failed to create user in Keycloak. Status: {}", response.getStatus());
                return false;
            }
        } catch (Exception e) {
            log.error("Exception during Keycloak registration: {}", e.getMessage(), e);
            return false;
        }
    }

    public KeycloakUserInfo findOrCreateTelegramKeycloakUser(Long telegramId, String firstName, String lastName, String username, String phoneNumber) {
        String primaryUsername = (username != null && !username.isBlank()) ? username : "telegram_" + telegramId;
        String fallbackUsername = "telegram_" + telegramId;
        try {
            RealmResource realmResource = keycloakAdminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> existingUsers = usersResource.search(primaryUsername, true);
            if (existingUsers.isEmpty() && !primaryUsername.equalsIgnoreCase(fallbackUsername)) {
                existingUsers = usersResource.search(fallbackUsername, true);
            }

            if (!existingUsers.isEmpty()) {
                UserRepresentation u = existingUsers.get(0);
                boolean updated = false;

                if (phoneNumber != null && !phoneNumber.isBlank()) {
                    u.singleAttribute("phoneNumber", phoneNumber);
                    updated = true;
                }
                if (username != null && !username.isBlank()) {
                    u.singleAttribute("telegramUsername", username);
                    updated = true;
                }
                // Self-heal accounts created before required actions were
                // cleared at creation time — otherwise they're permanently
                // stuck failing password-grant with "Account is not fully
                // set up" on every login attempt.
                if (u.getRequiredActions() != null && !u.getRequiredActions().isEmpty()) {
                    u.setRequiredActions(Collections.emptyList());
                    updated = true;
                }
                if (!Boolean.TRUE.equals(u.isEmailVerified())) {
                    u.setEmailVerified(true);
                    updated = true;
                }
                if (updated) {
                    try {
                        usersResource.get(u.getId()).update(u);
                    } catch (Exception ex) {
                        log.warn("Could not update Keycloak user attributes: {}", ex.getMessage());
                    }
                }

                String userPhone = (phoneNumber != null && !phoneNumber.isBlank()) ? phoneNumber : "N/A";
                if (u.getAttributes() != null && u.getAttributes().containsKey("phoneNumber")) {
                    List<String> phones = u.getAttributes().get("phoneNumber");
                    if (!phones.isEmpty() && phones.get(0) != null) {
                        userPhone = phones.get(0);
                    }
                }

                return new KeycloakUserInfo(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getFirstName(),
                        u.getLastName(),
                        userPhone
                );
            }

            UserRepresentation user = new UserRepresentation();
            user.setEnabled(true);
            user.setUsername(primaryUsername);
            user.setFirstName(firstName != null ? firstName : "Telegram");
            user.setLastName(lastName != null ? lastName : "User");
            user.setEmailVerified(true);
            // The realm's default required actions (verify email, update
            // password, etc.) get attached to every new user unless cleared
            // explicitly — left in place, they block password-grant login
            // with "Account is not fully set up" even though this account
            // was never meant to need any of them (there's no email to
            // verify and the password is a once-off, backend-generated one).
            user.setRequiredActions(Collections.emptyList());
            user.singleAttribute("telegramId", String.valueOf(telegramId));
            if (username != null && !username.isBlank()) {
                user.singleAttribute("telegramUsername", username);
            }
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                user.singleAttribute("phoneNumber", phoneNumber);
            }

            Response response = usersResource.create(user);
            if (response.getStatus() == 201) {
                log.info("Successfully registered Telegram user {} into Keycloak", primaryUsername);
                List<UserRepresentation> createdList = usersResource.search(primaryUsername, true);
                if (!createdList.isEmpty()) {
                    UserRepresentation created = createdList.get(0);
                    // Some Keycloak versions re-attach the realm's default
                    // required actions server-side right after creation,
                    // ignoring what was sent in the create payload above —
                    // so this is not redundant with setRequiredActions(...)
                    // on `user`. Confirmed by re-reading the user back and
                    // explicitly clearing it again post-creation.
                    if ((created.getRequiredActions() != null && !created.getRequiredActions().isEmpty())
                            || !Boolean.TRUE.equals(created.isEmailVerified())) {
                        created.setRequiredActions(Collections.emptyList());
                        created.setEmailVerified(true);
                        try {
                            usersResource.get(created.getId()).update(created);
                        } catch (Exception ex) {
                            log.warn("Could not clear required actions on newly created Telegram user {}: {}",
                                    primaryUsername, ex.getMessage());
                        }
                    }
                    return new KeycloakUserInfo(
                            created.getId(),
                            created.getUsername(),
                            created.getEmail(),
                            created.getFirstName(),
                            created.getLastName(),
                            phoneNumber != null ? phoneNumber : "N/A"
                    );
                }
            } else {
                log.error("Failed to create Telegram user in Keycloak. Status: {}", response.getStatus());
            }
        } catch (Exception e) {
            log.error("Exception in findOrCreateTelegramKeycloakUser for tgId {}: {}", telegramId, e.getMessage(), e);
        }
        return new KeycloakUserInfo("tg_" + telegramId, primaryUsername, null, firstName, lastName, phoneNumber != null ? phoneNumber : "N/A");
    }


    public KeycloakUserInfo loginAndFetchUserInfo(String emailOrUsername, String password) {
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(serverUrl)
                    .build();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                formData.add("client_secret", clientSecret);
            }
            formData.add("username", emailOrUsername);
            formData.add("password", password);

            Map<String, Object> tokenResponse = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                log.info("Successfully authenticated user {} via Keycloak Direct Grant", emailOrUsername);

                RealmResource realmResource = keycloakAdminClient.realm(realm);
                UsersResource usersResource = realmResource.users();

                List<UserRepresentation> searchResults = usersResource.search(emailOrUsername, true);
                if (searchResults.isEmpty()) {
                    searchResults = usersResource.searchByEmail(emailOrUsername, true);
                }

                if (!searchResults.isEmpty()) {
                    UserRepresentation userRep = searchResults.get(0);
                    String phone = "N/A";
                    if (userRep.getAttributes() != null && userRep.getAttributes().containsKey("phoneNumber")) {
                        List<String> phones = userRep.getAttributes().get("phoneNumber");
                        if (!phones.isEmpty()) {
                            phone = phones.get(0);
                        }
                    }

                    return new KeycloakUserInfo(
                            userRep.getId(),
                            userRep.getUsername(),
                            userRep.getEmail(),
                            userRep.getFirstName(),
                            userRep.getLastName(),
                            phone
                    );
                } else {
                    return new KeycloakUserInfo("unknown", emailOrUsername, emailOrUsername, "Keycloak", "User", "N/A");
                }
            }
            return null;
        }catch (org.springframework.web.client.RestClientResponseException e) {
            // 🔥 បន្ថែម Log នេះដើម្បីឲ្យឃើញ Error ពិតៗពី Keycloak លើ Console
            log.error("❌ Keycloak Login Rejected! HTTP Status: {}, Response Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Keycloak login failed for user {}: {}", emailOrUsername, e.getMessage());
            return null;
        }
    }
}