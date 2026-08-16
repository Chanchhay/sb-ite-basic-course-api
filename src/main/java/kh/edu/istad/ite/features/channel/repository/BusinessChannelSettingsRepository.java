package kh.edu.istad.ite.features.channel.repository;

import kh.edu.istad.ite.features.channel.entity.BusinessChannelSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessChannelSettingsRepository
        extends JpaRepository<BusinessChannelSettings, UUID> {

    Optional<BusinessChannelSettings> findByBusinessIdAndSalesChannelId(
            UUID businessId, UUID salesChannelId);

    Optional<BusinessChannelSettings> findByBusinessIdAndSalesChannelCode(
            UUID businessId, String salesChannelCode);

    List<BusinessChannelSettings> findByBusinessId(UUID businessId);
}
