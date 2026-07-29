package kh.edu.istad.ite.features.discount.repository;

import kh.edu.istad.ite.features.discount.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    List<Coupon> findAllByBusinessId(UUID businessId);
}
