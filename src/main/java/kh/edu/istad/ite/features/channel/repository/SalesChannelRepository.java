package kh.edu.istad.ite.features.channel.repository;

import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesChannelRepository extends JpaRepository<SalesChannel, UUID> {
    List<SalesChannel> findAllByIsActiveTrueOrderByNameAsc();
}
