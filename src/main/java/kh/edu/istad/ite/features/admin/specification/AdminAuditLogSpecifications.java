package kh.edu.istad.ite.features.admin.specification;

import kh.edu.istad.ite.features.admin.entity.AdminAuditLog;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public final class AdminAuditLogSpecifications {

    private AdminAuditLogSpecifications() {
    }

    public static Specification<AdminAuditLog> withFilters(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String actorId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword
    ) {
        Specification<AdminAuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (actionType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actionType"), actionType));
        }

        if (targetType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
        }

        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }

        if (StringUtils.hasText(actorId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId.trim()));
        }

        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }

        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("targetLabel")), pattern),
                    cb.like(cb.lower(root.get("actorUsername")), pattern),
                    cb.like(cb.lower(root.get("reason")), pattern)
            ));
        }

        return spec;
    }
}
