package kh.edu.istad.ite.config;

public final class CacheNames {

    /**
     * Namespace every cache key so one Redis instance can be shared with other
     * applications without a name clash.
     */
    private static final String KEY_NAMESPACE = "ite-sb-api::";

    public static final String CATALOG_ITEMS = "catalog:items";
    public static final String CATALOG_ITEM_BY_ID = "catalog:item-by-id";
    public static final String CATALOG_ITEM_BY_BARCODE = "catalog:item-by-barcode";
    public static final String CATALOG_ITEM_GROUPS = "catalog:item-groups";
    public static final String PUBLIC_STORE_ITEMS = "public:store-items";
    public static final String PUBLIC_STORE_ITEM_GROUPS = "public:store-item-groups";

    /**
     * The Redis key prefix a cache stores its entries under. Shared by the cache
     * configuration and {@link kh.edu.istad.ite.shared.cache.BusinessCacheEvictor},
     * which deletes a single business's entries by that prefix.
     */
    public static String keyPrefix(String cacheName) {
        return KEY_NAMESPACE + cacheName + "::";
    }

    private CacheNames() {
    }
}
