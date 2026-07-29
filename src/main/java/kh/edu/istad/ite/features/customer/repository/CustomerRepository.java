package kh.edu.istad.ite.features.customer.repository;

import kh.edu.istad.ite.features.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<Customer> findByBusiness_IdAndGlobalCustomer_Id(UUID businessId, UUID globalCustomerId);
}
