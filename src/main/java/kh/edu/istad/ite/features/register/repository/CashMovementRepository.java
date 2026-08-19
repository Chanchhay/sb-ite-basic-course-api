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

    /**
     * The same totals, for many sessions at once.
     *
     * A list of sessions needs one row per session and type, not one query
     * per session and type — the single-session method above is fine when a
     * cashier opens their own drawer, and quadratic when head office asks
     * for the day's history.
     */
    @Query("""
            SELECT cm.session.id AS sessionId, cm.type AS type, COALESCE(SUM(cm.amount), 0) AS total
            FROM CashMovement cm
            WHERE cm.session.id IN :sessionIds
            GROUP BY cm.session.id, cm.type
            """)
    List<SessionTypeTotal> sumAmountBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    interface SessionTypeTotal {
        Long getSessionId();
        CashMovementType getType();
        BigDecimal getTotal();
    }
}
