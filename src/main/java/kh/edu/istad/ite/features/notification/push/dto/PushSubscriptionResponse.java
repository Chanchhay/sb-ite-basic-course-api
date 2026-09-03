package kh.edu.istad.ite.features.notification.push.dto;

import java.util.UUID;

public record PushSubscriptionResponse(
        UUID userId,
        String endpoint,
        String p256dh,
        String auth,
        Long expirationTime
) {
}
