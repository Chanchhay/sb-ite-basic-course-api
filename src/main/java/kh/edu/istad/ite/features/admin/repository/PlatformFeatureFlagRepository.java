package kh.edu.istad.ite.features.admin.repository;

import kh.edu.istad.ite.features.admin.entity.PlatformFeatureFlag;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformFeatureFlagRepository extends JpaRepository<PlatformFeatureFlag, BusinessFeature> {

    Optional<PlatformFeatureFlag> findByFeature(BusinessFeature feature);

    boolean existsByFeatureAndEnabledFalse(BusinessFeature feature);
}
