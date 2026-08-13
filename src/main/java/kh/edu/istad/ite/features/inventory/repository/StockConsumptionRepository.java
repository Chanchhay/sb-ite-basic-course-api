package kh.edu.istad.ite.features.inventory.repository;

import kh.edu.istad.ite.features.inventory.entity.StockConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockConsumptionRepository extends JpaRepository<StockConsumption, UUID> {

    List<StockConsumption> findByStockEntry_IdOrderByCreatedDateAsc(UUID stockEntryId);
}
