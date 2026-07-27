package kh.edu.istad.ite.features.social.repository;

import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessTelegramBotRepository extends JpaRepository<BusinessTelegramBot, UUID> {

    Optional<BusinessTelegramBot> findByBusiness_Id(UUID businessId);

    // Used by the future webhook receiver to route an incoming Telegram update to the right business
    Optional<BusinessTelegramBot> findByWebhookSecret(String webhookSecret);
}
