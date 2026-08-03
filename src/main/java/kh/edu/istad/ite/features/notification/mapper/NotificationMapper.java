package kh.edu.istad.ite.features.notification.mapper;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.features.notification.dto.ReceivedNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationReceiver;
import kh.edu.istad.ite.features.notification.entity.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMapper {
    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;

    public ReceivedNotificationResponse toReceivedResponse(NotificationReceiver r) {
        NotificationSender s = r.getNotificationSender();
        String senderUsername = resolveSenderUsername(s.getSenderId(), s.getSenderName());

        return new ReceivedNotificationResponse(
                r.getId(), s.getId(), s.getSenderId(), senderUsername, s.getType(),
                s.getTitle(), s.getContent(), s.getDeepLink(),
                r.isRead(), r.getReadAt(), r.getDeliveredAt(), s.getCreatedDate());
    }

    private String resolveSenderUsername(String senderId, String senderName) {
        if (senderName != null && !senderName.isBlank() && !isUuid(senderName)) {
            return senderName;
        }
        if (senderId == null || senderId.isBlank()) {
            return "System";
        }
        if (isUuid(senderId)) {
            try {
                UserRepresentation user = keycloak.realm(props.getTargetRealm())
                        .users()
                        .get(senderId)
                        .toRepresentation();
                if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                    return user.getUsername();
                }
            } catch (Exception e) {
                log.debug("Could not resolve Keycloak user for ID {}", senderId);
            }
        }
        return senderId;
    }

    private boolean isUuid(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
