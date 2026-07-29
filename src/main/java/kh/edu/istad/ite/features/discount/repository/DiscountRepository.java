package kh.edu.istad.ite.features.discount.repository;

import kh.edu.istad.ite.features.discount.entity.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
//import java.awt.print.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID> {
    Page<Discount> findByBusinessId(UUID businessId, Pageable pageable);
    Optional<Discount> findByIdAndBusinessId(UUID id, UUID businessId);
}
