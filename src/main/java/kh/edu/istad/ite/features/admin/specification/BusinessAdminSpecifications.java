package kh.edu.istad.ite.features.admin.specification;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class BusinessAdminSpecifications {

    private BusinessAdminSpecifications() {
    }

    public static Specification<Business> withFilters(
            BusinessOwnerStatus status,
            Boolean isEnabled,
            Boolean isClosed,
            UUID categoryId,
            String keyword
    ) {
        Specification<Business> spec = (root, query, cb) -> cb.conjunction();

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (isEnabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isEnabled"), isEnabled));
        }

        if (isClosed != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isClosed"), isClosed));
        }

        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("businessCategory").get("id"), categoryId));
        }

        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("displayName")), pattern),
                    cb.like(cb.lower(root.get("businessEmail")), pattern),
                    cb.like(cb.lower(root.get("slug")), pattern)
            ));
        }

        return spec;
    }
}
