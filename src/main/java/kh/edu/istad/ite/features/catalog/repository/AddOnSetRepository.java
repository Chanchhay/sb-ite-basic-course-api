package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.AddOnSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnSetRepository extends JpaRepository<AddOnSet, UUID> {

    List<AddOnSet> findByBusinessIdOrderByNameAsc(UUID businessId);

    Page<AddOnSet> findByBusinessId(UUID businessId, Pageable pageable);

    Optional<AddOnSet> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);

    boolean existsByBusinessIdAndAddOnsId(UUID businessId, UUID addOnId);
}
