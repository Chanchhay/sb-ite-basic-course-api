package kh.edu.istad.ite.features.catalog.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.channel.entity.ItemChannel;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ItemSpecifications {

    public static Specification<Item> hasBusinessId(UUID businessId) {
        return (root, query, cb) -> cb.equal(root.get("business").get("id"), businessId);
    }

    public static Specification<Item> hasStatus(ItemStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Item> isNotDeleted() {
        return (root, query, cb) -> cb.or(cb.isNull(root.get("isDeleted")), cb.isFalse(root.get("isDeleted")));
    }

    public static Specification<Item> isDeleted() {
        return (root, query, cb) -> cb.isTrue(root.get("isDeleted"));
    }

    public static Specification<Item> hasItemGroupId(UUID itemGroupId) {
        return (root, query, cb) -> itemGroupId == null ? cb.conjunction() : cb.equal(root.get("itemGroup").get("id"), itemGroupId);
    }

    public static Specification<Item> hasPriceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Item> hasPriceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Item> nameContainsIgnoreCase(String name) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Item> isEnabledInChannelCodes(List<String> channelCodes) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<ItemChannel> itemChannelRoot = subquery.from(ItemChannel.class);
            Join<ItemChannel, SalesChannel> salesChannelJoin = itemChannelRoot.join("salesChannel");

            subquery.select(itemChannelRoot.get("item").get("id"))
                .where(
                    cb.equal(itemChannelRoot.get("item"), root),
                    salesChannelJoin.get("code").in(channelCodes),
                    cb.isTrue(itemChannelRoot.get("isEnabled"))
                );

            return cb.exists(subquery);
        };
    }
}
