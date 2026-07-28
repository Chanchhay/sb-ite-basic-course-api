package kh.edu.istad.ite.features.discount.repository;

import kh.edu.istad.ite.features.discount.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository extends
        JpaRepository<Discount, UUID>,
        JpaSpecificationExecutor<Discount> {

    Optional<Discount> findByIdAndBusinessIdAndDeletedAtIsNull(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(
            UUID businessId,
            String name,
            UUID id
    );
}
