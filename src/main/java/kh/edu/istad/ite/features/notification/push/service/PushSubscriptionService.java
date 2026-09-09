package kh.edu.istad.ite.features.notification.push.service;

import kh.edu.istad.ite.features.notification.push.dto.PushSubscriptionResponse;
import kh.edu.istad.ite.features.notification.push.dto.SubscribePushRequest;

import java.util.List;
import java.util.UUID;

public interface PushSubscriptionService {

    void subscribe(UUID userId, SubscribePushRequest request);

    void unsubscribe(UUID userId, String endpoint);

    /** For the dashboard's own webhook caller — no user in the loop to scope this to. */
    List<PushSubscriptionResponse> findForUsers(List<UUID> userIds);

    /** Drops a dead registration wherever it is — a push service answered 404/410 for it. */
    void deleteByEndpoint(String endpoint);
}
