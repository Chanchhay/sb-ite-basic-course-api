package kh.edu.istad.ite.features.business.repository;

import kh.edu.istad.ite.features.business.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    boolean existsByKeycloakUserId(UUID keycloakUserId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Optional<Business> findByKeycloakUserId(UUID keycloakUserId);

    Optional<Business> findByIdAndKeycloakUserId(UUID id, UUID keycloakUserId);
}
