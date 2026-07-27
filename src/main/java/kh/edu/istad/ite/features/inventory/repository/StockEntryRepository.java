package kh.edu.istad.ite.features.inventory.repository;

import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockEntryRepository extends JpaRepository<StockEntry, UUID>, JpaSpecificationExecutor<StockEntry> {

    Optional<StockEntry> findByIdAndBusiness_Id(UUID id, UUID businessId);

    Optional<StockEntry> findFirstByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(UUID businessId, UUID itemId);

    Optional<StockEntry> findAllByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(UUID businessId, UUID itemId);

    List<StockEntry> findAllByBusiness_IdOrderByCreatedDateDescIdDesc(UUID businessId);
}
