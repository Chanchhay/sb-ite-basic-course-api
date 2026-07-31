package kh.edu.istad.ite.features.user.repository;

import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    List<UserProfile> findByBusinessIdOrderByJoinedAtDesc(UUID businessId);

    Optional<UserProfile> findByUserIdAndBusinessId(UUID userId, UUID businessId);

    boolean existsByUserIdAndBusinessIdAndUserStatus(UUID userId, UUID businessId, RecordStatus userStatus);

    long countByBusinessIdAndUserStatus(UUID businessId, RecordStatus userStatus);
}
