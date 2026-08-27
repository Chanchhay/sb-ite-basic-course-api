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
            String province,
            String district,
            String cityOrProvince,
            String keyword
    ) {
        Specification<Business> spec = publiclyVisible();

        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("businessCategory").get("id"), categoryId));
        }

        // Matches whichever field is a business's *effective* province —
        // provinceName when the location rework has reached it, its old
        // cityOrProvince text otherwise. The filter dropdown offers exactly
        // this same COALESCE'd value (see findDistinctProvinceNames), so a
        // business never becomes unreachable just for not having been
        // migrated yet, and a plain equality is enough either way: neither
        // side is free-typed once it's the value driving this filter — one
        // comes from a geocoder, the other from the picklist itself.
        if (StringUtils.hasText(province)) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.lower(cb.coalesce(root.get("provinceName"), root.get("cityOrProvince"))),
                    province.trim().toLowerCase()
            ));
        } else if (StringUtils.hasText(cityOrProvince)) {
            // Only reached by a caller that still sends the old standalone
            // param name without `province` — kept for callers mid-rollout.
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.lower(root.get("cityOrProvince")), cityOrProvince.trim().toLowerCase()
            ));
        }

        if (StringUtils.hasText(district)) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.lower(root.get("districtName")), district.trim().toLowerCase()
            ));
        }

        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("displayName")), pattern),
                    cb.like(cb.lower(root.get("about")), pattern),
                    cb.like(cb.lower(root.get("cityOrProvince")), pattern),
                    cb.like(cb.lower(root.get("provinceName")), pattern)
            ));
        }

        return spec;
    }
}
