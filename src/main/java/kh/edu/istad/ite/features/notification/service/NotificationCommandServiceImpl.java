package kh.edu.istad.ite.features.notification.service;

import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.features.notification.dto.CreateNotificationRequest;
import kh.edu.istad.ite.features.notification.dto.CreateNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationReceiver;
import kh.edu.istad.ite.features.notification.entity.NotificationSender;
import kh.edu.istad.ite.features.notification.repository.NotificationReceiverRepository;
import kh.edu.istad.ite.features.notification.repository.NotificationSenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCommandServiceImpl implements NotificationCommandService {

    private final NotificationSenderRepository senderRepository;
    private final NotificationReceiverRepository receiverRepository;
    private final NotificationWebSocketPublisher webSocketPublisher;
    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;

    @Override
    @Transactional
    public CreateNotificationResponse send(UUID userId, CreateNotificationRequest request) {
        List<String> recipients = request.receiverIds().stream().distinct().toList();

        String resolvedSenderName = resolveSenderUsername(request.senderId(), request.senderName());

        NotificationSender sender = senderRepository.save(
                NotificationSender.create(userId, request.senderId(), resolvedSenderName, recipients,
                        request.type(), request.title(), request.content(), request.deepLink()));

        List<NotificationReceiver> receivers = recipients.stream()
                .map(rid -> {
                    UUID targetUserId = userId;
                    if (isUuid(rid)) {
                        targetUserId = UUID.fromString(rid);
                    }
                    return NotificationReceiver.create(targetUserId, sender, rid);
                })
                .toList();
        List<NotificationReceiver> savedReceivers = receiverRepository.saveAll(receivers);
        receiverRepository.flush();
        savedReceivers.forEach(webSocketPublisher::publish);
        return new CreateNotificationResponse(sender.getId(), savedReceivers.size());
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

    @Override
    @Transactional
    public void markAsRead(UUID userId, UUID receiverRecordId) {
        find(userId, receiverRecordId).markRead(LocalDateTime.now());
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId, String receiverId) {
        receiverRepository.markAllRead(userId, receiverId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID receiverRecordId) {
        find(userId, receiverRecordId).softDelete();
    }

    @Override
    @Transactional
    public void hardDelete(UUID userId, UUID receiverRecordId) {
        NotificationReceiver r = receiverRepository
                .findByIdAndDeletedFalse(receiverRecordId)
                .filter(rec -> rec.getUserId().equals(userId) || rec.getReceiverId().equals(userId.toString()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + receiverRecordId));
        receiverRepository.delete(r);
    }

    private NotificationReceiver find(UUID userId, UUID id) {
        return receiverRepository.findByIdAndDeletedFalse(id)
                .filter(r -> r.getUserId().equals(userId) || r.getReceiverId().equals(userId.toString()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + id));
    }
}
