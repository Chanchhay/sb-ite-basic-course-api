package kh.edu.istad.ite.config;

import org.springframework.data.domain.Pageable;

import java.util.Locale;
import java.util.UUID;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String item(UUID businessId, UUID itemId) {
        return businessId + ":" + itemId;
    }

    public static String barcode(UUID businessId, String barcode) {
        return businessId + ":" + trim(barcode);
    }

    public static String page(UUID businessId, Pageable pageable) {
        return businessId + ":" + page(pageable);
    }

    public static String store(String slugOrId) {
        return normalize(slugOrId);
    }

    /** A storefront as seen from somewhere: the same shop, with the distance to it. */
    public static String store(String slugOrId, Double lat, Double lng) {
        return normalize(slugOrId) + ":" + coordinate(lat) + ":" + coordinate(lng);
    }

    public static String storeList(
            UUID categoryId,
            String province,
            String district,
            String cityOrProvince,
            String keyword,
            Double lat,
            Double lng,
            Pageable pageable) {
        return categoryId
                + ":" + normalize(province)
                + ":" + normalize(district)
                + ":" + normalize(cityOrProvince)
                + ":" + normalize(keyword)
                + ":" + coordinate(lat)
                + ":" + coordinate(lng)
                + ":" + page(pageable);
    }

    public static String recommendedStores(UUID categoryId, Double lat, Double lng, Pageable pageable) {
        return categoryId
                + ":" + coordinate(lat)
                + ":" + coordinate(lng)
                + ":" + page(pageable);
    }

    /**
     * Four decimal places is about eleven metres — finer than any distance this
     * shows and finer than a phone's own fix, so rounding never moves a "2.3 km
     * away" label, while a shopper paging through results or changing a filter
     * keeps hitting the same entry instead of re-running the ranking.
     */
    private static String coordinate(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.4f", value);
    }

    private static String page(Pageable pageable) {
        return "page=" + pageable.getPageNumber()
                + ":size=" + pageable.getPageSize()
                + ":sort=" + pageable.getSort();
    }

    private static String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
