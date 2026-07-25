package kh.edu.istad.ite.features.admin.mapper;

import kh.edu.istad.ite.features.admin.dto.response.AdminAuditLogResponse;
import kh.edu.istad.ite.features.admin.entity.AdminAuditLog;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditLogMapper {

    public AdminAuditLogResponse toResponse(AdminAuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }

        return new AdminAuditLogResponse(
                auditLog.getId(),
                auditLog.getActorId(),
                auditLog.getActorUsername(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getTargetLabel(),
                auditLog.getPreviousState(),
                auditLog.getNewState(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getCreatedAt()
        );
    }
}
