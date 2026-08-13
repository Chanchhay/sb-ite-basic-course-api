package kh.edu.istad.ite.features.channel.repository;

import kh.edu.istad.ite.features.channel.entity.ItemChannelStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemChannelStockRepository extends JpaRepository<ItemChannelStock, UUID> {

    /** Every allocation an item has, across its channels and options. */
    @Query("""
        SELECT s FROM ItemChannelStock s
        JOIN FETCH s.salesChannel c
        LEFT JOIN FETCH s.variant v
        WHERE s.item.id = :itemId
    """)
    List<ItemChannelStock> findByItemId(@Param("itemId") UUID itemId);

    /**
     * The one allocation a sale draws on.
     *
     * The option is part of the key, and null is a value it takes: an item
     * with no options allocates against the item itself, so the null case is
     * spelled out rather than left to a parameter that would never match.
     */
    @Query("""
        SELECT s FROM ItemChannelStock s
        WHERE s.item.id = :itemId
          AND s.salesChannel.id = :channelId
          AND ((:variantId IS NULL AND s.variant IS NULL) OR s.variant.id = :variantId)
    """)
    Optional<ItemChannelStock> findOne(
            @Param("itemId") UUID itemId,
            @Param("channelId") UUID channelId,
            @Param("variantId") UUID variantId);

    /**
     * Every ceiling one channel is under, for one business.
     *
     * Narrowed to items the shop has actually split: an item on SHARED has no
     * ceiling, and a row saying so would only be a number the till has to
     * ignore. One read serves a whole till screen.
     */
    @Query("""
        SELECT s FROM ItemChannelStock s
        JOIN FETCH s.item i
        LEFT JOIN FETCH s.variant v
        WHERE s.salesChannel.code = :channelCode
          AND i.business.id = :businessId
          AND i.channelStockMode = kh.edu.istad.ite.shared.enums.ChannelStockMode.ALLOCATED
    """)
    List<ItemChannelStock> findByChannelCodeAndBusinessId(
            @Param("channelCode") String channelCode,
            @Param("businessId") UUID businessId);

    void deleteByItemId(UUID itemId);
}
