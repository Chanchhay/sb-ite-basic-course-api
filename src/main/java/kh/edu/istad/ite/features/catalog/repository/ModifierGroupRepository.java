package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, UUID> {

    List<ModifierGroup> findAllByItemIdAndBusinessIdOrderBySortOrderAsc(UUID itemId, UUID businessId);

    Optional<ModifierGroup> findByIdAndItemIdAndBusinessId(UUID id, UUID itemId, UUID businessId);

    boolean existsByItemIdAndBusinessIdAndNameIgnoreCase(UUID itemId, UUID businessId, String name);

    boolean existsByItemIdAndBusinessIdAndNameIgnoreCaseAndIdNot(
            UUID itemId, UUID businessId, String name, UUID id);
}