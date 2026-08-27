package kh.edu.istad.ite.features.customer.repository;

import kh.edu.istad.ite.features.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Customer> findAllByBusinessIdOrderByCreatedDateDesc(UUID businessId);

    Page<Customer>findAllByBusinessId(UUID businessId, Pageable pageable);

    boolean existsByMembershipType_Id(UUID membershipTypeId);

    Optional<Customer> findByBusiness_IdAndGlobalCustomer_Id(UUID businessId, UUID globalCustomerId);

    List<Customer> findAllByGlobalCustomer_Id(UUID globalCustomerId);
}
