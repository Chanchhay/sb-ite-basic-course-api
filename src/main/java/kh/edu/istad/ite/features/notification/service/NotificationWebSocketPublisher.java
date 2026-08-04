package kh.edu.istad.ite.features.notification.service;

import kh.edu.istad.ite.features.notification.dto.ReceivedNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationReceiver;
import kh.edu.istad.ite.features.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NotificationWebSocketPublisher {
    private static final String TOPIC_PREFIX = "/topic/notifications/";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationMapper mapper;

    public void publish(NotificationReceiver receiver) {
        ReceivedNotificationResponse payload = mapper.toReceivedResponse(receiver);
        String tenantUserDestination = TOPIC_PREFIX + receiver.getUserId() + "/" + receiver.getReceiverId();
        String userDestination = TOPIC_PREFIX + receiver.getReceiverId();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAll(tenantUserDestination, userDestination, payload);
                }
            });
            return;
        }

        sendAll(tenantUserDestination, userDestination, payload);
    }

    private void sendAll(String tenantUserDestination, String userDestination, ReceivedNotificationResponse payload) {
        messagingTemplate.convertAndSend(tenantUserDestination, payload);
        messagingTemplate.convertAndSend(userDestination, payload);
        messagingTemplate.convertAndSend("/topic/notifications", payload);
    }
}
