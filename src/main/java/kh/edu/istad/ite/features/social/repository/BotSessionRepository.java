package kh.edu.istad.ite.features.social.repository;

import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.shared.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BotSessionRepository extends JpaRepository<BotSession, UUID> {

    Optional<BotSession> findByBusiness_IdAndChannelAndExternalId(
            UUID businessId, ChannelType channel, String externalId);

    Optional<BotSession> findByBusiness_IdAndChannelAndCustomer_Id(
            UUID businessId, ChannelType channel, UUID customerId);
}
