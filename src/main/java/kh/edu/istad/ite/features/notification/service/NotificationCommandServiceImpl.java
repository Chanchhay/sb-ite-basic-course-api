package kh.edu.istad.ite.features.notification.service;

import kh.edu.istad.ite.features.notification.dto.CreateNotificationRequest;
import kh.edu.istad.ite.features.notification.dto.CreateNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationReceiver;
import kh.edu.istad.ite.features.notification.entity.NotificationSender;
import kh.edu.istad.ite.features.notification.repository.NotificationReceiverRepository;
import kh.edu.istad.ite.features.notification.repository.NotificationSenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationCommandServiceImpl implements NotificationCommandService {

    private final NotificationSenderRepository senderRepository;
    private final NotificationReceiverRepository receiverRepository;

    @Override
    @Transactional
    public CreateNotificationResponse send(UUID userId, CreateNotificationRequest request) {
        List<String> recipients = request.receiverIds().stream().distinct().toList();

        NotificationSender sender = senderRepository.save(
                NotificationSender.create(userId, request.senderId(), recipients,
                        request.type(), request.title(), request.content(), request.deepLink()));

        List<NotificationReceiver> receivers = recipients.stream()
                .map(rid -> NotificationReceiver.create(userId, sender, rid))
                .toList();
        receiverRepository.saveAll(receivers);
        return new CreateNotificationResponse(sender.getId(), receivers.size());
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
                .findByIdAndUserId(receiverRecordId, userId)   // find WITHOUT the deletedFalse filter
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + receiverRecordId));
        receiverRepository.delete(r);
    }

    private NotificationReceiver find(UUID userId, UUID id) {
        return receiverRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + id));
    }
}