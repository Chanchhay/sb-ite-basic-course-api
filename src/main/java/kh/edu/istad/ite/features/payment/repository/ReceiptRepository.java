package kh.edu.istad.ite.features.payment.repository;

import kh.edu.istad.ite.features.payment.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByOrder_IdAndBusiness_Id(UUID orderId, UUID businessId);

    boolean existsByOrder_Id(UUID orderId);
}
