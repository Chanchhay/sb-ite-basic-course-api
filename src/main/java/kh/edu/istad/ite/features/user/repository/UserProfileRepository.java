package kh.edu.istad.ite.features.user.repository;

import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    List<UserProfile> findByBusinessIdOrderByJoinedAtDesc(UUID businessId);

    Page<UserProfile> findByBusinessId(UUID businessId, Pageable pageable);

    Optional<UserProfile> findByUserIdAndBusinessId(UUID userId, UUID businessId);

    boolean existsByUserIdAndBusinessIdAndStaffStatus(UUID userId, UUID businessId, RecordStatus staffStatus);

    /**
     * The business a staff member works in, for callers that know who is asking
     * but not which business. `userId` is the primary key, so this is at most
     * one row; the status and business conditions are what make it a lookup
     * rather than a `findById`. Owners are resolved by ownership before this is
     * tried — they have a profile too, but no business on it.
     */
    Optional<UserProfile> findFirstByUserIdAndStaffStatusAndBusinessIsNotNull(
            UUID userId, RecordStatus staffStatus);

    long countByBusinessIdAndStaffStatus(UUID businessId, RecordStatus staffStatus);
}
