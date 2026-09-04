package kh.edu.istad.ite.features.register.repository;

import kh.edu.istad.ite.features.register.entity.RegisterSession;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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


}
