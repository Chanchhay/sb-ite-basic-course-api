package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.response.AdminAuditLogResponse;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AdminAuditLogService {

    /** Records an action that has no meaningful before/after state. */
    void record(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            String reason
    );

    /** Records an action that moves the target from one state to another. */
    void recordStateChange(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            String previousState,
            String newState,
            String reason
    );

    Page<AdminAuditLogResponse> getAuditLogs(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String actorId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    );

    AdminAuditLogResponse getAuditLog(UUID auditLogId);
}
