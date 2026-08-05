package kh.edu.istad.ite.features.channel.repository;

import kh.edu.istad.ite.features.channel.entity.ItemChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemChannelRepository extends JpaRepository<ItemChannel, UUID> {
        boolean existsByItemIdAndSalesChannelId(
                        UUID itemId,
                        UUID salesChannelId);

        Optional<ItemChannel> findByItemIdAndSalesChannelId(
                        UUID itemId,
                        UUID salesChannelId);

        List<ItemChannel> findByItemId(UUID itemId);

        List<ItemChannel> findBySalesChannelIdAndIsEnabledTrue(
                        UUID salesChannelId);

        @Query("SELECT ic FROM ItemChannel ic JOIN FETCH ic.item i WHERE ic.salesChannel.code = :code AND ic.isEnabled = true")
        List<ItemChannel> findBySalesChannelCodeAndIsEnabledTrue(
                        @org.springframework.data.repository.query.Param("code") String code);

        @Query("""
            SELECT ic FROM ItemChannel ic
            JOIN FETCH ic.item i
            WHERE ic.salesChannel.code = :code
              AND ic.isEnabled = true
              AND i.business.id = :businessId
        """)
        List<ItemChannel> findBySalesChannelCodeAndBusinessIdAndIsEnabledTrue(
                        @org.springframework.data.repository.query.Param("code") String code,
                        @org.springframework.data.repository.query.Param("businessId") UUID businessId);
}
