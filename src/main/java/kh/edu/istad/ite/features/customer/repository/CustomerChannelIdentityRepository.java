package kh.edu.istad.ite.features.customer.repository;

import kh.edu.istad.ite.features.customer.entity.CustomerChannelIdentity;
import kh.edu.istad.ite.shared.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerChannelIdentityRepository extends JpaRepository<CustomerChannelIdentity, UUID> {

    Optional<CustomerChannelIdentity> findByBusiness_IdAndChannelAndExternalId(
            UUID businessId, ChannelType channel, String externalId);
}
