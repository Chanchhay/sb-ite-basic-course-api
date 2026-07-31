package kh.edu.istad.ite.features.payment.repository;

import kh.edu.istad.ite.features.payment.entity.PaymentQrCode;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.QrStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentQrCodeRepository extends JpaRepository<PaymentQrCode, UUID> {

    Optional<PaymentQrCode> findByMd5Hash(String md5Hash);

    List<PaymentQrCode> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @Query("""
            SELECT q FROM PaymentQrCode q
            JOIN FETCH q.order o
            JOIN FETCH q.business b
            WHERE q.status = :qrStatus
              AND o.status = :orderStatus
              AND o.channel = :channel
            ORDER BY q.createdAt ASC
            """)
    List<PaymentQrCode> findOutstandingByChannel(
            @Param("qrStatus") QrStatus qrStatus,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("channel") OrderChannel channel);
}
