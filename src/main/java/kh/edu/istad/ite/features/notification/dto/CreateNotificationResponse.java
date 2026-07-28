package kh.edu.istad.ite.features.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateNotificationResponse(UUID notificationId, int recipientCount) {}
