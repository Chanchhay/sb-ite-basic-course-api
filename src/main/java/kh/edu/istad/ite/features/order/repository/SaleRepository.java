package kh.edu.istad.ite.features.order.repository;

import kh.edu.istad.ite.features.order.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
