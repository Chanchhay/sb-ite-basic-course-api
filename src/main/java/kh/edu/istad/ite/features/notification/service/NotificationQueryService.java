package kh.edu.istad.ite.features.notification.service;

import kh.edu.istad.ite.features.notification.dto.ReceivedNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationQueryService {
    Page<ReceivedNotificationResponse> getReceived(UUID userId, NotificationType type,
                                                   Boolean isRead, Pageable pageable);
    long getUnreadCount(UUID userId, String receiverId);
}
