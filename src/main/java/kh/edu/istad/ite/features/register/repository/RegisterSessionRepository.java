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

    /**
     * How many drawers are open right now.
     *
     * Deliberately unfiltered by anything the screen is searching for: "two
     * tills are open" is a fact about the shop this minute, and narrowing it
     * to whatever the reader typed into a search box would make it a different,
     * much less useful number.
     */
    long countByBusinessIdAndStatus(java.util.UUID businessId, SessionStatus status);

    /**
     * The cash the filtered shifts took, summed across all of them.
     *
     * Native because it spans two tables whose timestamps are not the same
     * kind: a session's {@code opened_at} is an instant, a sale's
     * {@code sold_at} is wall-clock with no zone. The zone to read the instant
     * in is passed in rather than taken from the database session, so the
     * boundary lands where the application says it does and not wherever the
     * connection happened to be configured.
     */
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
