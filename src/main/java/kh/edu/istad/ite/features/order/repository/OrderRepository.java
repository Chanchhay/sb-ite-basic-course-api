package kh.edu.istad.ite.features.order.repository;

import kh.edu.istad.ite.features.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessId(UUID businessId);
}
