package kh.edu.istad.ite.features.register.repository;

import kh.edu.istad.ite.features.register.entity.RegisterSession;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegisterSessionRepository extends JpaRepository<RegisterSession, Long> {
    Optional<RegisterSession> findByRegisterIdAndStatus(Long registerId, SessionStatus status);
    Optional<RegisterSession> findByCashierIdAndStatus(String cashierId, SessionStatus status);
}
