package kh.edu.istad.ite.features.notification.push.service;

import kh.edu.istad.ite.features.notification.push.dto.PushSubscriptionResponse;
import kh.edu.istad.ite.features.notification.push.dto.SubscribePushRequest;
import kh.edu.istad.ite.features.notification.push.entity.PushSubscription;
import kh.edu.istad.ite.features.notification.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushSubscriptionServiceImpl implements PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    /**
     * Keyed by endpoint, not by (userId, endpoint): the same browser
     * subscribing again — after a token refresh, or a different user
     * signing into the same kiosk — is still one device with one thing to
     * say about it, not a second row alongside a now-stale one.
     */
    @Override
    @Transactional
    public void subscribe(UUID userId, SubscribePushRequest request) {
        PushSubscription subscription = repository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);

        subscription.setUserId(userId);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscription.setExpirationTime(request.expirationTime());

        repository.save(subscription);
    }

    @Override
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushSubscriptionResponse> findForUsers(List<UUID> userIds) {
        return repository.findByUserIdIn(userIds).stream()
                .map(sub -> new PushSubscriptionResponse(
                        sub.getUserId(),
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        sub.getExpirationTime()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteByEndpoint(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
