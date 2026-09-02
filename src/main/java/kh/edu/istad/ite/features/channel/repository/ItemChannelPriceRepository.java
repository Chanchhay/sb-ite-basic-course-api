package kh.edu.istad.ite.features.channel.repository;

import kh.edu.istad.ite.features.channel.entity.ItemChannelPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemChannelPriceRepository extends JpaRepository<ItemChannelPrice, UUID> {

    /** Every exception this channel makes, for one business's items only. */
    @Query("""
        SELECT p FROM ItemChannelPrice p
        WHERE p.salesChannel.id = :channelId
          AND p.item.business.id = :businessId
    """)
    List<ItemChannelPrice> findForBusinessChannel(
            @Param("businessId") UUID businessId,
            @Param("channelId") UUID channelId);

    /** The one exception that could apply to a line being priced. */
    @Query("""
        SELECT p FROM ItemChannelPrice p
        WHERE p.salesChannel.code = :channelCode
          AND p.item.id = :itemId
          AND ((:variantId IS NULL AND p.variant IS NULL) OR p.variant.id = :variantId)
          AND ((:unitId IS NULL AND p.unit IS NULL) OR p.unit.id = :unitId)
    """)
    Optional<ItemChannelPrice> findLine(
            @Param("channelCode") String channelCode,
            @Param("itemId") UUID itemId,
            @Param("variantId") UUID variantId,
            @Param("unitId") UUID unitId);

    List<ItemChannelPrice> findByItemId(UUID itemId);
}
