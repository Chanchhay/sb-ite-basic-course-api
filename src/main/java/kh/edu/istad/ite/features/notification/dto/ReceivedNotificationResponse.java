package kh.edu.istad.ite.features.notification.dto;


import kh.edu.istad.ite.features.notification.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReceivedNotificationResponse(
        UUID id,                 // receiver row id -> use to mark read / delete
        UUID notificationId,
        String senderId,
        NotificationType type,
        String title,
        String content,
        String deepLink,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {}