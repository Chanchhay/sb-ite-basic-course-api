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
            UUID salesChannelId
    );


    Optional<ItemChannel> findByItemIdAndSalesChannelId(
            UUID itemId,
            UUID salesChannelId
    );


    List<ItemChannel> findByItemId(UUID itemId);


    List<ItemChannel> findBySalesChannelIdAndIsEnabledTrue(
            UUID salesChannelId
    );
}
