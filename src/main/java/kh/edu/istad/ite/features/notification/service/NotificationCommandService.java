package kh.edu.istad.ite.features.notification.service;

import kh.edu.istad.ite.features.notification.dto.CreateNotificationRequest;
import kh.edu.istad.ite.features.notification.dto.CreateNotificationResponse;

import java.util.UUID;

public interface NotificationCommandService {
    CreateNotificationResponse send(UUID userId, CreateNotificationRequest request);
    void markAsRead(UUID userId, UUID receiverRecordId);
    void markAllAsRead(UUID userId, String receiverId);
    void delete(UUID userId, UUID receiverRecordId);
    void hardDelete(UUID userId, UUID receiverRecordId);
}
