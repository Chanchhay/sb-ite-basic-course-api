package kh.edu.istad.ite.features.social.repository;

import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessTelegramBotRepository extends JpaRepository<BusinessTelegramBot, UUID> {

    Optional<BusinessTelegramBot> findByBusiness_Id(UUID businessId);

    Optional<BusinessTelegramBot> findByWebhookSecret(String webhookSecret);

    long countByIsActiveTrue();

    List<BusinessTelegramBot> findAllByOrderByCreatedDateDesc();
}
