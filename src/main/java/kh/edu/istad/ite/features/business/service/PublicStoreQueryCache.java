package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.config.CacheNames;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.mapper.StorefrontMapper;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.business.specification.PublicStoreSpecifications;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.helper.GeoDistanceHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The marketplace listings, cached.
 * <p>
 * These are separated from {@code StorefrontServiceImpl} for two reasons. Caching
 * only works through the proxy, so a cached read has to be called from outside the
 * bean that owns it. And the results are paged: {@code Page} has no stable
 * serialized form to store and read back, so what is cached is a
 * {@link PageResponse} and the caller rebuilds the page around it — the same
 * arrangement the catalog queries use.
 * <p>
 * Nearby search is the reason this is worth caching at all. Ranking by distance
 * cannot be done in SQL here, so every request with coordinates loads all matching
 * shops, measures each one and sorts them in memory. That is affordable once and
 * wasteful on the second page, on a filter change that keeps the same location, and
 * on every repeat of a search the storefront fans out per selected category.
 */
@Component
@RequiredArgsConstructor
class PublicStoreQueryCache {

    private final BusinessRepository businessRepository;
    private final StorefrontMapper storefrontMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PUBLIC_STORE_LIST,
            key = "T(kh.edu.istad.ite.config.CacheKeys).storeList(#p0, #p1, #p2, #p3, #p4, #p5, #p6, #p7)")
    public PageResponse<PublicStoreResponse> findPublicStores(
            UUID categoryId,
            String province,
            String district,
            String cityOrProvince,
            String keyword,
            Double lat,
            Double lng,
            Pageable pageable) {

        Specification<Business> spec =
                PublicStoreSpecifications.withFilters(categoryId, province, district, cityOrProvince, keyword);

        if (lat == null || lng == null) {
            return PageResponse.from(
                    businessRepository.findAll(spec, pageable).map(storefrontMapper::toPublicResponse));
        }

        List<Business> matches = businessRepository.findAll(spec);

        List<Map.Entry<Business, Double>> ranked = matches.stream()
                .map(business -> (Map.Entry<Business, Double>)
                        new java.util.AbstractMap.SimpleEntry<>(business, distanceKm(business, lat, lng)))
                .sorted(Comparator.comparing(
                        Map.Entry::getValue,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        int start = Math.min((int) pageable.getOffset(), ranked.size());
        int end = Math.min(start + pageable.getPageSize(), ranked.size());

        List<PublicStoreResponse> pageContent = ranked.subList(start, end).stream()
                .map(entry -> storefrontMapper.toPublicResponse(entry.getKey(), entry.getValue()))
                .toList();

        return PageResponse.from(new PageImpl<>(pageContent, pageable, ranked.size()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PUBLIC_STORE_RECOMMENDED,
            key = "T(kh.edu.istad.ite.config.CacheKeys).recommendedStores(#p0, #p1, #p2, #p3)")
    public PageResponse<PublicStoreResponse> findRecommendedStores(
            UUID categoryId, Double lat, Double lng, Pageable pageable) {

        // Ranking here stays sales/recency-based — "recommended" isn't "nearest" —
        // this only annotates each card with its distance for display, same as the
        // plain listing does.
        if (lat == null || lng == null) {
            return PageResponse.from(
                    businessRepository.findRecommendedStores(categoryId, pageable)
                            .map(storefrontMapper::toPublicResponse));
        }

        return PageResponse.from(
                businessRepository.findRecommendedStores(categoryId, pageable)
                        .map(business -> storefrontMapper.toPublicResponse(business, distanceKm(business, lat, lng))));
    }

    /** Null when the business hasn't dropped a map pin yet — nothing to rank it by. */
    private Double distanceKm(Business business, double lat, double lng) {
        if (business.getLatitude() == null || business.getLongitude() == null) {
            return null;
        }
        return GeoDistanceHelper.haversineKm(
                lat, lng, business.getLatitude().doubleValue(), business.getLongitude().doubleValue());
    }
}
