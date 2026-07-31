package kh.edu.istad.ite.features.notification.service;

import kh.edu.istad.ite.features.notification.dto.ReceivedNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import kh.edu.istad.ite.features.notification.mapper.NotificationMapper;
import kh.edu.istad.ite.features.notification.repository.NotificationReceiverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService
{
    private final NotificationReceiverRepository receiverRepository;
    private final NotificationMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ReceivedNotificationResponse> getReceived(UUID userId, NotificationType type,
                                                          Boolean isRead, Pageable pageable) {
        return receiverRepository
                .findInbox(userId, userId.toString(), type, isRead, pageable)
                .map(mapper::toReceivedResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId, String receiverId) {
        return receiverRepository
                .countByUserIdAndReceiverIdAndReadFalseAndDeletedFalse(userId, receiverId);
    }
}
