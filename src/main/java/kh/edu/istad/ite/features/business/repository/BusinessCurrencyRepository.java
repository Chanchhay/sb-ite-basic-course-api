package kh.edu.istad.ite.features.business.repository;

import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessCurrencyRepository extends JpaRepository<BusinessCurrency, UUID> {

    List<BusinessCurrency> findAllByBusinessIdOrderByCodeAsc(UUID businessId);

    Optional<BusinessCurrency> findByBusinessIdAndCodeIgnoreCase(UUID businessId, String code);

    boolean existsByBusinessIdAndCodeIgnoreCase(UUID businessId, String code);

    long countByBusinessId(UUID businessId);
}
