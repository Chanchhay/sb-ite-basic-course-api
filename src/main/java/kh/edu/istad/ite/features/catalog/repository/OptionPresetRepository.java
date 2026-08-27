package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.OptionPreset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptionPresetRepository extends JpaRepository<OptionPreset, UUID> {

    List<OptionPreset> findByBusinessIdOrderByNameAsc(UUID businessId);

    Page<OptionPreset> findByBusinessId(UUID businessId, Pageable pageable);

    Optional<OptionPreset> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);
}
