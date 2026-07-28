package kh.edu.istad.ite.features.business.specification;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessFeatureFlag;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class PublicStoreSpecifications {

    private PublicStoreSpecifications() {
    }

    public static Specification<Business> publiclyVisible() {
        return (root, query, cb) -> {
            Subquery<UUID> disabled = query.subquery(UUID.class);
            Root<BusinessFeatureFlag> flag = disabled.from(BusinessFeatureFlag.class);

            disabled.select(flag.get("business").get("id"))
                    .where(cb.and(
                            cb.equal(flag.get("business").get("id"), root.get("id")),
                            cb.equal(flag.get("feature"), BusinessFeature.STOREFRONT),
                            cb.isFalse(flag.get("enabled"))
                    ));

            return cb.and(
                    cb.isTrue(root.get("isListing")),
                    cb.isTrue(root.get("isEnabled")),
                    cb.isFalse(root.get("isClosed")),
                    cb.equal(root.get("status"), BusinessOwnerStatus.ACTIVE),
                    cb.not(cb.exists(disabled))
            );
        };
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
