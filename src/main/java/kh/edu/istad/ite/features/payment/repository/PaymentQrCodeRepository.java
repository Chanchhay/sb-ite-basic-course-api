package kh.edu.istad.ite.features.payment.repository;

import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentQrCodeRepository extends JpaRepository<PaymentQrCode, UUID> {

    Optional<PaymentQrCode> findByMd5Hash(String md5Hash);

    List<PaymentQrCode> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
