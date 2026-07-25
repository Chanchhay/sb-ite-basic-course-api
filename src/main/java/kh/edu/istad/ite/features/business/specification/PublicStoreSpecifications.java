package kh.edu.istad.ite.features.business.specification;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class PublicStoreSpecifications {

    private PublicStoreSpecifications() {
    }

    /**
     * A store is publicly visible only when the owner published it AND the
     * platform has not restricted it. All four conditions are required.
     */
    public static Specification<Business> publiclyVisible() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("isListing")),
                cb.isTrue(root.get("isEnabled")),
                cb.isFalse(root.get("isClosed")),
                cb.equal(root.get("status"), BusinessOwnerStatus.ACTIVE)
        );
    }

    public static Specification<Business> withFilters(
            UUID categoryId,
            String cityOrProvince,
            String keyword
    ) {
        Specification<Business> spec = publiclyVisible();

        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("businessCategory").get("id"), categoryId));
        }

        if (StringUtils.hasText(cityOrProvince)) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.lower(root.get("cityOrProvince")), cityOrProvince.trim().toLowerCase()
            ));
        }

        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("displayName")), pattern),
                    cb.like(cb.lower(root.get("about")), pattern),
                    cb.like(cb.lower(root.get("cityOrProvince")), pattern)
            ));
        }

        return spec;
    }
}
