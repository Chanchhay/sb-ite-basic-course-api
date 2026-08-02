package kh.edu.istad.ite.features.discount.repository;

import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    Optional<Discount> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Discount> findAllByBusinessIdOrderByCreatedDateDesc(UUID businessId);

    @Query("""
        SELECT d FROM Discount d
        WHERE d.business.id = :businessId
          AND d.status = :status
          AND (d.startsAt IS NULL OR d.startsAt <= :now)
          AND (d.endsAt IS NULL OR d.endsAt >= :now)
        ORDER BY d.createdDate DESC
    """)
    List<Discount> findActiveDiscountsByBusinessId(
            @Param("businessId") UUID businessId,
            @Param("status") RecordStatus status,
            @Param("now") LocalDateTime now
    );

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);
}
