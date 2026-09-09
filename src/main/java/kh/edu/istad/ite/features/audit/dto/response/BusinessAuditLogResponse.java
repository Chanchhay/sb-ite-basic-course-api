package kh.edu.istad.ite.features.audit.dto.response;

import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;

import java.time.LocalDateTime;
import java.util.UUID;

public record BusinessAuditLogResponse(
        UUID id,
        String actorId,
        String actorUsername,
        BusinessAuditAction actionType,
        BusinessAuditTarget targetType,
        String targetId,
        String targetLabel,
        String previousState,
        String newState,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {
}
