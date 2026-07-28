package kh.edu.istad.ite.features.business.repository;

import kh.edu.istad.ite.features.business.entity.BusinessFeatureFlag;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessFeatureFlagRepository extends JpaRepository<BusinessFeatureFlag, UUID> {

    List<BusinessFeatureFlag> findAllByBusinessId(UUID businessId);

    Optional<BusinessFeatureFlag> findByBusinessIdAndFeature(UUID businessId, BusinessFeature feature);

    /** Absence means enabled, so only an explicit false counts as disabled. */
    boolean existsByBusinessIdAndFeatureAndEnabledFalse(UUID businessId, BusinessFeature feature);

    long countByFeatureAndEnabledFalse(BusinessFeature feature);
}
