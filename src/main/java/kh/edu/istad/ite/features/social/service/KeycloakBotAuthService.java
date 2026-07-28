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