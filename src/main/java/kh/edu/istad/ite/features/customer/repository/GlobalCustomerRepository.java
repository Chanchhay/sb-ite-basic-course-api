package kh.edu.istad.ite.features.customer.repository;

import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GlobalCustomerRepository extends JpaRepository<GlobalCustomer, UUID> {

    Optional<GlobalCustomer> findByPhoneNumber(String phoneNumber);
}
