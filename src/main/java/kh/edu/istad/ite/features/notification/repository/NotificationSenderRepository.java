package kh.edu.istad.ite.features.notification.repository;

import kh.edu.istad.ite.features.notification.entity.NotificationSender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationSenderRepository extends JpaRepository<NotificationSender, UUID> {

}
