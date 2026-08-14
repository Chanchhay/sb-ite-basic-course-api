package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.ItemUomConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemUomConversionRepository extends JpaRepository<ItemUomConversion, UUID> {

    boolean existsByUnitId(UUID unitId);
}
