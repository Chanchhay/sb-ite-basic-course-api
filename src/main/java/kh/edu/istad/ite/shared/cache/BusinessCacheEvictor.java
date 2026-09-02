package kh.edu.istad.ite.shared.cache;

import kh.edu.istad.ite.config.CacheNames;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Drops the cached reads that a write to one business has invalidated.
 * <p>
 * This replaces {@code @CacheEvict(allEntries = true)} on the write paths. Clearing
 * whole caches was correct but far too wide: editing one item in one shop threw away
 * every other shop's cached storefront, so an active platform would keep knocking its
 * own cache out from under itself. Every cache key already starts with the business
 * that owns the data — {@code businessId:…} for the catalog caches, the storefront
 * slug for the public ones — so a write only has to delete that one business's keys.
 * <p>
 * Deletion runs after the transaction commits, matching the cache manager's
 * {@code transactionAware()} behaviour: evicting mid-transaction would let a
 * concurrent reader re-cache the pre-commit state and leave it stale.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BusinessCacheEvictor {

    /** Keys are written with a plain String serializer, so they scan back as Strings. */
    private final StringRedisTemplate redisTemplate;

    private final BusinessRepository businessRepository;

    private static final String[] CATALOG_CACHES = {
            CacheNames.CATALOG_ITEMS,
            CacheNames.CATALOG_ITEM_BY_ID,
            CacheNames.CATALOG_ITEM_BY_BARCODE,
            CacheNames.CATALOG_ITEM_GROUPS
    };

    private static final String[] STOREFRONT_CACHES = {
            CacheNames.PUBLIC_STORE_ITEMS,
            CacheNames.PUBLIC_STORE_ITEM_GROUPS
    };

    /**
     * Invalidates what shoppers see: the public item list and menu of this business's
     * storefront. For writes that change availability, price or stock but leave the
     * back-office catalog views untouched.
     * <p>
     * Safe to call as soon as the write is decided, including at the top of the
     * method: the deletion itself is held back until the transaction commits.
     */
    public void evictStorefront(UUID businessId) {
        evict(businessId, false);
    }

    /**
     * Invalidates both the storefront and the owner-facing catalog reads of this
     * business. For writes to items and item groups themselves.
     * <p>
     * Safe to call as soon as the write is decided, including at the top of the
     * method: the deletion itself is held back until the transaction commits.
     */
    public void evictCatalogAndStorefront(UUID businessId) {
        evict(businessId, true);
    }

    private void evict(UUID businessId, boolean includeCatalog) {
        if (businessId == null) {
            return;
        }

        // Resolved now rather than in the callback: the storefront caches are keyed by
        // slug, and by the time the transaction commits the business may have been
        // deleted and the slug become unreadable.
        String slug = businessRepository.findById(businessId)
                .map(business -> business.getSlug())
                .orElse(null);

        afterCommit(() -> {
            for (String cacheName : STOREFRONT_CACHES) {
                // A storefront is reachable by slug or by id, and each spelling is
                // cached under its own key.
                deleteKey(CacheNames.keyPrefix(cacheName) + normalized(businessId.toString()));
                if (slug != null) {
                    deleteKey(CacheNames.keyPrefix(cacheName) + normalized(slug));
                }
            }

            if (includeCatalog) {
                for (String cacheName : CATALOG_CACHES) {
                    deleteByPrefix(CacheNames.keyPrefix(cacheName) + businessId + ":");
                }
            }
        });
    }

    /** Mirrors {@code CacheKeys}, which lowercases the slug so lookups are case-insensitive. */
    private String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            run(action);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                run(action);
            }
        });
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            // The write itself succeeded and must stand. Cached entries carry short
            // TTLs, so the worst case is a few minutes of staleness rather than a
            // failed request.
            log.warn("Could not evict cache entries; stale entries will expire on their own", ex);
        }
    }

    private void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    private void deleteByPrefix(String prefix) {
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(256).build();

        List<String> batch = new ArrayList<>();
        try (Cursor<String> keys = redisTemplate.scan(options)) {
            while (keys.hasNext()) {
                batch.add(keys.next());
                if (batch.size() >= 256) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            redisTemplate.delete(batch);
        }
    }
}
