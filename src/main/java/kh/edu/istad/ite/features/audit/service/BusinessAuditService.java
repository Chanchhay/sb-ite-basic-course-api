package kh.edu.istad.ite.features.audit.service;

import kh.edu.istad.ite.features.audit.dto.response.BusinessAuditLogResponse;
import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface BusinessAuditService {

    /**
     * Records something a shop's own staff did.
     *
     * Never throws. An audit entry that fails must not take the action it was
     * describing down with it — refusing to suspend a staff member because the
     * log could not be written is a worse outcome than an incomplete log, and
     * the caller has already done the work by the time this is reached.
     */
    void record(
            UUID businessId,
            BusinessAuditAction actionType,
            BusinessAuditTarget targetType,
            String targetId,
            String targetLabel,
            String previousState,
            String newState);

    /** Records a sign-in, at most once per Keycloak session. */
    void recordSignIn(UUID businessId, String sessionId);

    Page<BusinessAuditLogResponse> getAuditLogs(
            UUID businessId,
            BusinessAuditAction actionType,
            BusinessAuditTarget targetType,
            String actorId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable);
}
