package kh.edu.istad.ite.features.customer.repository;

import kh.edu.istad.ite.features.customer.entity.MembershipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipTypeRepository extends JpaRepository<MembershipType, UUID> {

    Optional<MembershipType> findByIdAndBusinessId(UUID id, UUID businessId);

    List<MembershipType> findAllByBusinessIdOrderByTypeNameAsc(UUID businessId);

    boolean existsByBusinessIdAndTypeNameIgnoreCase(UUID businessId, String typeName);

    boolean existsByBusinessIdAndTypeNameIgnoreCaseAndIdNot(UUID businessId, String typeName, UUID id);

    boolean existsByDiscount_Id(UUID discountId);
}
