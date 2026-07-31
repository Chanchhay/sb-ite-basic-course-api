package kh.edu.istad.ite.features.notification.mapper;

import kh.edu.istad.ite.features.notification.dto.ReceivedNotificationResponse;
import kh.edu.istad.ite.features.notification.entity.NotificationReceiver;
import kh.edu.istad.ite.features.notification.entity.NotificationSender;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public ReceivedNotificationResponse toReceivedResponse(NotificationReceiver r) {
        NotificationSender s = r.getNotificationSender();
        return new ReceivedNotificationResponse(
                r.getId(), s.getId(), s.getSenderId(), s.getType(),
                s.getTitle(), s.getContent(), s.getDeepLink(),
                r.isRead(), r.getReadAt(), r.getDeliveredAt(), s.getCreatedDate());
    }
}
