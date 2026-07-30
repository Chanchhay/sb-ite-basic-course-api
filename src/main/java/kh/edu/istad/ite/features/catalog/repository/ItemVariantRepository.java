package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ItemVariantRepository extends JpaRepository<ItemVariant, UUID> {

    Optional<ItemVariant> findByIdAndBusiness_Id(UUID id, UUID businessId);
}