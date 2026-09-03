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
     * "Which business am I?" — the answer the dashboard asks for before nearly
     * every other call it makes, and which for a given user essentially never
     * changes. Keyed by the signed-in user rather than by business, because
     * resolving the business is the whole point of the lookup.
     */
    public static final String BUSINESS_ME = "business:me";

    public static final String PUBLIC_STORE_DETAIL = "public:store-detail";
    public static final String PUBLIC_STORE_FACEBOOK = "public:store-facebook";

    /**
     * The marketplace side of the storefront: which shops exist and where. Unlike
     * the caches above, an entry here spans every business rather than belonging
     * to one, so it is invalidated by a shop appearing, disappearing or moving —
     * never by a shop editing its own menu.
     */
    public static final String PUBLIC_STORE_LIST = "public:store-list";
    public static final String PUBLIC_STORE_RECOMMENDED = "public:store-recommended";
    public static final String PUBLIC_STORE_PROVINCES = "public:store-provinces";

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
