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
        return businessId
                + ":page=" + pageable.getPageNumber()
                + ":size=" + pageable.getPageSize()
                + ":sort=" + pageable.getSort();
    }

    public static String store(String slugOrId) {
        return normalize(slugOrId);
    }

    private static String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
