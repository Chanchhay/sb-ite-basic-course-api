package kh.edu.istad.ite.features.discount.specification;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public final class DiscountSpecifications {

    private DiscountSpecifications() {
    }

    /** Base predicate: belongs to the business and has not been soft-deleted. */
    public static Specification<Discount> belongsToBusiness(UUID businessId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("business").get("id"), businessId),
                cb.isNull(root.get("deletedAt"))
        );
    }

    public static Specification<Discount> withFilters(
            UUID businessId,
            String keyword,
            DiscountType type,
            DiscountRuleType ruleType,
            DiscountScope scope,
            RecordStatus status,
            LocalDateTime activeAt
    ) {
        Specification<Discount> spec = belongsToBusiness(businessId);

        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }

        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }

        if (ruleType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ruleType"), ruleType));
        }

        if (scope != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("scope"), scope));
        }

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (activeAt != null) {
            spec = spec.and((root, query, cb) -> {
                Predicate startedOk = cb.or(
                        cb.isNull(root.get("startsAt")),
                        cb.lessThanOrEqualTo(root.get("startsAt"), activeAt)
                );
                Predicate notEndedOk = cb.or(
                        cb.isNull(root.get("endsAt")),
                        cb.greaterThanOrEqualTo(root.get("endsAt"), activeAt)
                );
                return cb.and(startedOk, notEndedOk);
            });
        }

        return spec;
    }
}
