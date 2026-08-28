package kh.edu.istad.ite.features.register.repository;

import kh.edu.istad.ite.features.register.entity.RegisterSession;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface RegisterSessionRepository
        extends JpaRepository<RegisterSession, Long>, JpaSpecificationExecutor<RegisterSession> {
    Optional<RegisterSession> findByRegisterIdAndStatus(Long registerId, SessionStatus status);
    Optional<RegisterSession> findByUserIdAndStatus(String userId, SessionStatus status);
    Optional<RegisterSession> findByBusinessIdAndStatus(java.util.UUID businessId, SessionStatus status);
    @EntityGraph(attributePaths = "register")
    Page<RegisterSession> findByBusinessId(java.util.UUID businessId, Pageable pageable);


    long countByBusinessIdAndStatus(java.util.UUID businessId, SessionStatus status);


    @Query(value = """
            select coalesce(sum(sa.total_amount), 0)
            from register_sessions rs
            join sales sa
              on sa.cashier_id::text = rs.user_id
             and sa.payment_method = 'CASH'
             and sa.sold_at >= (rs.opened_at at time zone :zone)
             and sa.sold_at <= coalesce(rs.closed_at at time zone :zone, now() at time zone :zone)
            where rs.id = any(:sessionIds)
            """, nativeQuery = true)
    BigDecimal sumCashSalesForSessions(
            @Param("sessionIds") Long[] sessionIds,
            @Param("zone") String zone);
}
