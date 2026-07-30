package kh.edu.istad.ite.features.discount.repository;

import kh.edu.istad.ite.features.discount.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Coupon> findAllByBusinessIdOrderByCreatedDateDesc(UUID businessId);

    List<Coupon> findAllByBusinessIdAndDiscount_IdOrderByCreatedDateDesc(UUID businessId, UUID discountId);

    Optional<Coupon> findByBusinessIdAndCodeIgnoreCase(UUID businessId, String code);

    boolean existsByBusinessIdAndCodeIgnoreCase(UUID businessId, String code);

    boolean existsByBusinessIdAndCodeIgnoreCaseAndIdNot(UUID businessId, String code, UUID id);

    boolean existsByDiscount_Id(UUID discountId);
}
