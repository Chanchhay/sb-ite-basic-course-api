package kh.edu.istad.ite.features.notification.push.repository;

import kh.edu.istad.ite.features.notification.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserIdIn(Collection<UUID> userIds);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);

    void deleteByEndpoint(String endpoint);
}
