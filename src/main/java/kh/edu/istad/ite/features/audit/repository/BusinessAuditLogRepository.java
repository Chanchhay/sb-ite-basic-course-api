package kh.edu.istad.ite.features.audit.repository;

import kh.edu.istad.ite.features.audit.entity.BusinessAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BusinessAuditLogRepository
        extends JpaRepository<BusinessAuditLog, UUID>, JpaSpecificationExecutor<BusinessAuditLog> {

    boolean existsByBusinessIdAndSessionId(UUID businessId, String sessionId);
}
