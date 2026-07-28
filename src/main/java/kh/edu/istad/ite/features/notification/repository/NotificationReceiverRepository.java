package kh.edu.istad.ite.features.notification.repository;

import kh.edu.istad.ite.features.notification.entity.NotificationReceiver;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationReceiverRepository extends JpaRepository<NotificationReceiver, UUID>, JpaSpecificationExecutor<NotificationReceiver> {
    Optional<NotificationReceiver> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    long countByUserIdAndReceiverIdAndReadFalseAndDeletedFalse(UUID userId, String receiverId);

    Optional<NotificationReceiver> findByIdAndUserId(UUID id, UUID userId);
    @Query("""
            select r from NotificationReceiver r
            join fetch r.notificationSender s
            where r.userId = :userId
              and r.receiverId = :receiverId
              and r.deleted = false
              and s.deleted = false
              and (:type   is null or s.type = :type)
              and (:isRead is null or r.read = :isRead)
            """)
    Page<NotificationReceiver> findInbox(@Param("userId") UUID userId,
                                         @Param("receiverId") String receiverId,
                                         @Param("type") NotificationType type,
                                         @Param("isRead") Boolean isRead,
                                         Pageable pageable);
    @Modifying
    @Query("""
            update NotificationReceiver r
               set r.read = true, r.readAt = :when
             where r.userId = :userId and r.receiverId = :receiverId
               and r.read = false and r.deleted = false
            """)
    int markAllRead(@Param("userId") UUID userId,
                    @Param("receiverId") String receiverId,
                    @Param("when") LocalDateTime when);
}
