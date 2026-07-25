package kh.edu.istad.ite.features.admin.dto.response;

import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminAuditLogResponse(
        UUID id,
        String actorId,
        String actorUsername,
        AdminActionType actionType,
        AuditTargetType targetType,
        UUID targetId,
        String targetLabel,
        String previousState,
        String newState,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {
}
