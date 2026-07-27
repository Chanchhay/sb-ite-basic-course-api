package kh.edu.istad.ite.features.register.repository;

import kh.edu.istad.ite.features.register.entity.CashMovement;
import kh.edu.istad.ite.shared.enums.CashMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findBySessionId(Long sessionId);

    @Query("SELECT COALESCE(SUM(cm.amount), 0) FROM CashMovement cm WHERE cm.session.id = :sessionId AND cm.type = :type")
    BigDecimal sumAmountBySessionIdAndType(@Param("sessionId") Long sessionId, @Param("type") CashMovementType type);
}
